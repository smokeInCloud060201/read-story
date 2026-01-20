use actix_web::{get, post, delete, web, HttpResponse, Responder};
use crate::db;
use sqlx::{Pool, Postgres};

#[derive(serde::Deserialize)]
pub struct BookmarkParams {
    #[serde(rename = "storyId")]
    pub story_id: i64,
    #[serde(rename = "chapterId")]
    pub chapter_id: i64,
}

#[post("/bookmarks")]
pub async fn save_bookmark(
    pool: web::Data<Pool<Postgres>>,
    query: web::Query<BookmarkParams>,
) -> impl Responder {
    match db::save_bookmark(&pool, query.story_id, query.chapter_id).await {
        Ok(_) => HttpResponse::Ok().finish(),
        Err(e) => {
            log::error!("Database error: {}", e);
            HttpResponse::InternalServerError().finish()
        }
    }
}

#[get("/bookmarks")]
pub async fn list_bookmarks(pool: web::Data<Pool<Postgres>>) -> impl Responder {
    match db::find_all_bookmarks(&pool).await {
        Ok(bookmarks) => HttpResponse::Ok().json(bookmarks),
        Err(e) => {
            log::error!("Database error: {}", e);
            HttpResponse::InternalServerError().finish()
        }
    }
}

#[delete("/bookmarks/{id}")]
pub async fn delete_bookmark(
    pool: web::Data<Pool<Postgres>>,
    id: web::Path<i64>,
) -> impl Responder {
    match db::delete_bookmark(&pool, id.into_inner()).await {
        Ok(_) => HttpResponse::Ok().finish(),
        Err(e) => {
            log::error!("Database error: {}", e);
            HttpResponse::InternalServerError().finish()
        }
    }
}
