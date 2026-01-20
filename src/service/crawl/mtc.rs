use async_trait::async_trait;
use scraper::{Html, Selector};
use reqwest::Client;
use crate::models::Story;
use crate::service::crawl::{CrawlStrategy, ChapterTask};
use serde::Deserialize;
use std::sync::RwLock;
use aes::cipher::{block_padding::Pkcs7, BlockModeDecrypt, KeyIvInit};
use base64::{Engine as _, engine::general_purpose};
use regex::Regex;

type Aes128CbcDec = cbc::Decryptor<aes::Aes128>;

pub struct MTCCrawlStrategy {
    base_url: String,
    backend_url: String,
    cached_key: RwLock<Option<String>>,
}

#[derive(Deserialize)]
struct MTCChapterApiData {
    name: String,
    index: String,
}

#[derive(Deserialize)]
struct MTCChaptersResponse {
    data: Vec<MTCChapterApiData>,
}

impl MTCCrawlStrategy {
    pub fn new() -> Self {
        Self {
            base_url: "https://metruyencv.com".to_string(),
            backend_url: "https://backend.metruyencv.com".to_string(),
            cached_key: RwLock::new(None),
        }
    }

    fn decrypt(&self, cipher_text: &str) -> String {
        if cipher_text.is_empty() { return String::new(); }

        let key_str = self.cached_key.read().unwrap().clone()
            .unwrap_or_else(|| "cNdR17YqKmWx9BgT".to_string());
        
        let key = key_str.as_bytes();
        let iv = key_str.as_bytes();

        let Ok(cipher_bytes) = general_purpose::STANDARD.decode(cipher_text) else {
            return String::new();
        };

        if cipher_bytes.is_empty() { return String::new(); }

        let decryptor = Aes128CbcDec::new(
            key.try_into().expect("invalid key length"),
            iv.try_into().expect("invalid iv length"),
        );
        
        let mut buf = cipher_bytes.to_vec();
        match decryptor.decrypt_padded::<Pkcs7>(&mut buf) {
            Ok(decrypted) => String::from_utf8_lossy(decrypted).to_string(),
            Err(_) => String::new(),
        }
    }

    async fn do_refresh_key(&self, js_url: &str, client: &Client) {
        log::info!("Fetching JS bundle for key extraction: {}", js_url);
        let Ok(response) = client.get(js_url).send().await else { return; };
        let Ok(js_content) = response.text().await else { return; };

        let re = Regex::new(r"\[(\d+(?:,\s*\d+){15})\]").unwrap();
        if let Some(caps) = re.captures(&js_content) {
            let codes_str = caps.get(1).unwrap().as_str();
            let mut key_chars: Vec<char> = codes_str.split(',')
                .map(|s| s.trim().parse::<u8>().unwrap_or(0) as char)
                .collect();
            key_chars.reverse();
            let new_key: String = key_chars.into_iter().collect();
            
            let mut cache = self.cached_key.write().unwrap();
            *cache = Some(new_key.clone());
            log::info!("Successfully extracted and cached AES key: {}", new_key);
        }
    }
}

#[async_trait]
impl CrawlStrategy for MTCCrawlStrategy {
    fn supports(&self, url: &str) -> bool {
        url.starts_with(&self.base_url)
    }

    async fn parse_story(&self, url: &str, client: &Client) -> Result<Story, Box<dyn std::error::Error + Send + Sync>> {
        let story_slug = url.split('/').last().ok_or("Invalid URL")?;
        
        let response = client.get(url).send().await?.text().await?;
        
        let title = {
            let document = Html::parse_document(&response);
            let h1_selector = Selector::parse("h1").unwrap();
            document.select(&h1_selector).next()
                .map(|el| el.text().collect::<String>())
                .unwrap_or_else(|| story_slug.to_string())
        };

        Ok(Story {
            id: 0,
            name: story_slug.to_string(),
            title: Some(title),
            source: Some("MTC".to_string()),
            created_at: None,
        })
    }

    async fn parse_chapter_tasks(&self, url: &str, _story: &Story, client: &Client) -> Result<Vec<ChapterTask>, Box<dyn std::error::Error + Send + Sync>> {
        let response = client.get(url).send().await?.text().await?;
        
        let book_id = {
            let document = Html::parse_document(&response);
            let script_selector = Selector::parse("script").unwrap();
            let mut id = String::new();
            
            for script in document.select(&script_selector) {
                let text = script.text().collect::<String>();
                if text.contains("window.bookData") {
                    let re = Regex::new(r#""id":\s*(\d+)"#).unwrap();
                    if let Some(caps) = re.captures(&text) {
                        id = caps.get(1).unwrap().as_str().to_string();
                        break;
                    }
                }
            }
            id
        };

        if book_id.is_empty() {
            return Err("Could not find book_id".into());
        }

        let api_url = format!("{}/api/chapters?filter[book_id]={}&filter[type]=published", self.backend_url, book_id);
        let api_response = client.get(&api_url).send().await?.json::<MTCChaptersResponse>().await?;

        let tasks = api_response.data.into_iter().map(|d| ChapterTask {
            name: d.name,
            index: d.index.parse().unwrap_or(0),
            raw_key: d.index,
        }).collect();

        Ok(tasks)
    }

    async fn parse_chapter(&self, story: &Story, task: &ChapterTask, client: &Client) -> Result<(String, Option<String>), Box<dyn std::error::Error + Send + Sync>> {
        let chapter_url = format!("{}/truyen/{}/chuong-{}", self.base_url, story.name, task.index);
        
        let response = client.get(&chapter_url).send().await?.text().await?;
        
        let (encoded_content, js_url) = {
            let document = Html::parse_document(&response);

            let mut js_url = None;
            let needs_refresh = self.cached_key.read().unwrap().is_none();
            if needs_refresh {
                let js_selector = Selector::parse("script[src*='/build/assets/app-']").unwrap();
                if let Some(script_tag) = document.select(&js_selector).next() {
                    let mut url = script_tag.value().attr("src").unwrap_or_default().to_string();
                    if url.starts_with('/') {
                        url = format!("{}{}", self.base_url, url);
                    }
                    js_url = Some(url);
                }
            }

            let script_selector = Selector::parse("script").unwrap();
            let mut content = String::new();

            for script in document.select(&script_selector) {
                let text = script.text().collect::<String>();
                if text.contains("window.chapterData") {
                    let re = Regex::new(r#"content\s*:\s*"([^"]+)""#).unwrap();
                    if let Some(caps) = re.captures(&text) {
                        content = caps.get(1).unwrap().as_str().replace("\\/", "/");
                        break;
                    }
                }
            }
            (content, js_url)
        };

        if let Some(url) = js_url {
            self.do_refresh_key(&url, client).await;
        }

        if encoded_content.is_empty() {
            return Err("Could not find encoded content".into());
        }

        let decoded = self.decrypt(&encoded_content);
        Ok((decoded, None))
    }
}
