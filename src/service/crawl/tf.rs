use async_trait::async_trait;
use scraper::{Html, Selector};
use reqwest::Client;
use crate::models::Story;
use crate::service::crawl::{CrawlStrategy, ChapterTask};

pub struct TFCrawlStrategy {
    base_url: String,
}

impl TFCrawlStrategy {
    pub fn new() -> Self {
        Self {
            base_url: "https://truyenfull.vision".to_string(),
        }
    }
}

#[async_trait]
impl CrawlStrategy for TFCrawlStrategy {
    fn supports(&self, url: &str) -> bool {
        url.starts_with(&self.base_url)
    }

    async fn parse_story(&self, url: &str, _client: &Client) -> Result<Story, Box<dyn std::error::Error + Send + Sync>> {
        let story_name = url.trim_end_matches('/').split('/').last().ok_or("Invalid URL")?;
        
        Ok(Story {
            id: 0,
            name: story_name.to_string(),
            title: Some(story_name.to_string()),
            source: Some("TRUYEN_FULL".to_string()),
            created_at: None,
        })
    }

    async fn parse_chapter_tasks(&self, url: &str, _story: &Story, client: &Client) -> Result<Vec<ChapterTask>, Box<dyn std::error::Error + Send + Sync>> {
        let response = client.get(url).send().await?.text().await?;
        
        let story_id_raw = {
            let document = Html::parse_document(&response);
            let id_selector = Selector::parse("#truyen-id").unwrap();
            document.select(&id_selector).next()
                .and_then(|el| el.value().attr("value"))
                .map(|s| s.to_string())
                .ok_or_else(|| Box::<dyn std::error::Error + Send + Sync>::from("Could not find truyen-id"))?
        };

        let ajax_url = format!("{}/ajax.php?type=chapter_option&data={}", self.base_url, story_id_raw);
        let ajax_response = client.get(&ajax_url).send().await?.text().await?;
        
        let mut tasks = Vec::new();
        {
            let ajax_doc = Html::parse_document(&ajax_response);
            let chapter_selector = Selector::parse("select.chapter_jump option").unwrap();

            for el in ajax_doc.select(&chapter_selector) {
                let title = el.text().collect::<String>();
                let raw_key = el.value().attr("value").unwrap_or_default();
                let key_parts: Vec<&str> = raw_key.split('-').collect();
                if key_parts.len() < 2 { continue; }
                
                let key = key_parts.last().unwrap().parse::<i32>().unwrap_or(0);

                tasks.push(ChapterTask {
                    name: title,
                    index: key,
                    raw_key: raw_key.to_string(),
                });
            }
        }

        Ok(tasks)
    }

    async fn parse_chapter(&self, story: &Story, task: &ChapterTask, client: &Client) -> Result<(String, Option<String>), Box<dyn std::error::Error + Send + Sync>> {
        let chapter_url = format!("{}/{}/{}", self.base_url, story.name, task.raw_key);
        log::info!("Fetching chapter content from: {}", chapter_url);

        let response = client.get(&chapter_url).send().await?.text().await?;
        
        let (content, title) = {
            let document = Html::parse_document(&response);
            let container_selector = Selector::parse("#chapter-big-container").unwrap();
            let content_selector = Selector::parse("#chapter-c").unwrap();
            let title_selector = Selector::parse("a.chapter-title").unwrap();

            let title_text = if let Some(container) = document.select(&container_selector).next() {
                container.select(&title_selector).next()
                    .and_then(|el| el.value().attr("title"))
                    .map(|s| s.to_string())
            } else {
                document.select(&title_selector).next()
                     .and_then(|el| el.value().attr("title"))
                     .map(|s| s.to_string())
            };

            let content_text = if let Some(container) = document.select(&container_selector).next() {
                if let Some(content_el) = container.select(&content_selector).next() {
                    Ok::<String, Box<dyn std::error::Error + Send + Sync>>(content_el.html())
                } else {
                    Err(Box::<dyn std::error::Error + Send + Sync>::from("Could not find #chapter-c inside container"))
                }
            } else {
                if let Some(content_el) = document.select(&content_selector).next() {
                    Ok::<String, Box<dyn std::error::Error + Send + Sync>>(content_el.html())
                } else {
                    Err(Box::<dyn std::error::Error + Send + Sync>::from("Could not find chapter container or content"))
                }
            }?;

            (content_text, title_text)
        };

        Ok((content, title))
    }
}
