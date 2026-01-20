mod models;
mod db;
mod handlers;
mod service;

use actix_web::{web, App, HttpServer, middleware::Logger};
use actix_files as fs;
use dotenvy::dotenv;
use sqlx::postgres::PgPoolOptions;
use std::env;
use std::sync::Arc;
use crate::service::CrawlService;

#[actix_web::main]
async fn main() -> std::io::Result<()> {
    dotenv().ok();
    env_logger::init_from_env(env_logger::Env::new().default_filter_or("info"));

    let database_url = env::var("DATABASE_URL").expect("DATABASE_URL must be set");
    let pool = PgPoolOptions::new()
        .max_connections(5)
        .connect(&database_url)
        .await
        .expect("Failed to create pool");

    // Run migrations on startup
    sqlx::migrate!("./migrations")
        .run(&pool)
        .await
        .expect("Failed to run migrations");

    let crawl_service = web::Data::new(Arc::new(CrawlService::new(pool.clone())));

    let port = env::var("PORT").unwrap_or_else(|_| "8082".to_string());
    let bind_address = format!("0.0.0.0:{}", port);

    log::info!("Starting server at http://{}", bind_address);

    HttpServer::new(move || {
        App::new()
            .app_data(web::Data::new(pool.clone()))
            .app_data(crawl_service.clone())
            .wrap(Logger::default())
            // Static files
            .service(fs::Files::new("/static", "./static").show_files_listing())
            // View Routes
            .service(handlers::view::index)
            .service(handlers::view::list_stories)
            .service(handlers::view::list_chapters)
            .service(handlers::view::view_chapter)
            // API Routes (v1)
            .service(
                web::scope("/api/v1")
                    .service(handlers::bookmark::save_bookmark)
                    .service(handlers::bookmark::list_bookmarks)
                    .service(handlers::bookmark::delete_bookmark)
                    .service(handlers::crawl::crawl_story)
            )
    })
    .bind(bind_address)?
    .run()
    .await
}
