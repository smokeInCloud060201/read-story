use actix_web::{post, web, HttpResponse, Responder};
use crate::service::CrawlService;
use std::sync::Arc;

#[derive(serde::Deserialize)]
pub struct CrawlRequest {
    pub url: String,
}

#[post("/crawl")]
pub async fn crawl_story(
    service: web::Data<Arc<CrawlService>>,
    query: web::Query<CrawlRequest>,
) -> impl Responder {
    let url = query.url.clone();
    let service_inner = service.get_ref().clone();

    // Spawn the crawl task in the background
    tokio::spawn(async move {
        if let Err(e) = service_inner.crawl_story(url).await {
            log::error!("Background crawl failed: {}", e);
        }
    });

    // Return immediately to the caller
    HttpResponse::Accepted().json("Crawling started in background")
}
