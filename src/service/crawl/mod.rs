use async_trait::async_trait;
use crate::models::Story;
use sqlx::{Pool, Postgres};
use reqwest::Client;
use std::sync::Arc;
use tokio::time::{sleep, Duration};

pub mod tf;
pub mod mtc;

#[derive(Debug, Clone)]
pub struct ChapterTask {
    pub name: String,
    pub index: i32,
    pub raw_key: String,
}

#[async_trait]
pub trait CrawlStrategy: Send + Sync {
    fn supports(&self, url: &str) -> bool;
    async fn parse_story(&self, url: &str, client: &Client) -> Result<Story, Box<dyn std::error::Error + Send + Sync>>;
    async fn parse_chapter_tasks(&self, url: &str, story: &Story, client: &Client) -> Result<Vec<ChapterTask>, Box<dyn std::error::Error + Send + Sync>>;
    async fn parse_chapter(&self, story: &Story, task: &ChapterTask, client: &Client) -> Result<(String, Option<String>), Box<dyn std::error::Error + Send + Sync>>;
}

pub struct CrawlService {
    client: Client,
    pool: Pool<Postgres>,
    strategies: Vec<Box<dyn CrawlStrategy>>,
}

impl CrawlService {
    pub fn new(pool: Pool<Postgres>) -> Self {
        let client = Client::builder()
            .user_agent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .build()
            .expect("Failed to build reqwest client");

        Self {
            client,
            pool,
            strategies: vec![
                Box::new(tf::TFCrawlStrategy::new()),
                Box::new(mtc::MTCCrawlStrategy::new()),
            ],
        }
    }

    pub async fn crawl_story(self: Arc<Self>, url: String) -> Result<(), Box<dyn std::error::Error + Send + Sync>> {
        let strategy = self.strategies.iter()
            .find(|s| s.supports(&url))
            .ok_or_else(|| format!("No strategy found for URL: {}", url))?;

        log::info!("Starting background crawl for URL: {}", url);

        let story_info = strategy.parse_story(&url, &self.client).await?;
        
        let story = match crate::db::find_story_by_name(&self.pool, &story_info.name).await? {
            Some(s) => s,
            None => {
                sqlx::query_as::<_, Story>(
                    "INSERT INTO stories (name, title, source, created_at) VALUES ($1, $2, $3, NOW()) RETURNING id, name, title, source, created_at"
                )
                .bind(&story_info.name)
                .bind(story_info.title.as_ref().unwrap_or(&story_info.name))
                .bind(&story_info.source)
                .fetch_one(&self.pool)
                .await?
            }
        };

        let tasks = strategy.parse_chapter_tasks(&url, &story, &self.client).await?;
        log::info!("Found {} chapter tasks for {}", tasks.len(), story.name);

        // Filter out existing chapters to avoid duplicate crawling effort
        let mut tasks_to_crawl = Vec::new();
        for task in tasks {
            let existing = sqlx::query("SELECT id FROM chapter WHERE story_id = $1 AND key = $2")
                .bind(story.id)
                .bind(task.index)
                .fetch_optional(&self.pool)
                .await?;
            if existing.is_none() {
                tasks_to_crawl.push(task);
            }
        }

        if tasks_to_crawl.is_empty() {
            log::info!("No new chapters to crawl for {}", story.name);
            return Ok(());
        }

        let batch_size = 400;
        let total_batches = (tasks_to_crawl.len() as f64 / batch_size as f64).ceil() as usize;

        for (i, batch) in tasks_to_crawl.chunks(batch_size).enumerate() {
            log::info!("Processing batch {}/{} ({} chapters)", i + 1, total_batches, batch.len());
            
            let mut titles = Vec::new();
            let mut keys = Vec::new();
            let mut contents = Vec::new();

            for task in batch {
                match strategy.parse_chapter(&story, task, &self.client).await {
                    Ok((content, title_opt)) => {
                        let final_title = title_opt.unwrap_or(task.name.clone());
                        titles.push(final_title);
                        keys.push(task.index);
                        contents.push(content);
                    },
                    Err(e) => {
                        log::error!("Failed to crawl chapter {}: {}", task.name, e);
                    }
                }
            }

            if !titles.is_empty() {
                // Batch insert using UNNEST
                sqlx::query(
                    "INSERT INTO chapter (story_id, title, key, content) 
                     SELECT $1, * FROM UNNEST($2, $3, $4)"
                )
                .bind(story.id)
                .bind(&titles)
                .bind(&keys)
                .bind(&contents)
                .execute(&self.pool)
                .await?;
                
                log::info!("Batch {} inserted successfully", i + 1);
            }

            if i + 1 < total_batches {
                log::info!("Sleeping for 5 minutes before next batch...");
                sleep(Duration::from_secs(300)).await;
            }
        }

        log::info!("Crawl completed for story: {}", story.name);
        Ok(())
    }
}
