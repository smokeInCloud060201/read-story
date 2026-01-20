use sqlx::{Pool, Postgres, Row};
use crate::models::{Story, Chapter, Bookmark};

pub async fn find_all_stories(pool: &Pool<Postgres>) -> Result<Vec<Story>, sqlx::Error> {
    sqlx::query_as::<_, Story>("SELECT id, name, title, source, created_at FROM stories ORDER BY id DESC")
        .fetch_all(pool)
        .await
}

pub async fn find_story_by_name(pool: &Pool<Postgres>, name: &str) -> Result<Option<Story>, sqlx::Error> {
    sqlx::query_as::<_, Story>("SELECT id, name, title, source, created_at FROM stories WHERE name = $1")
        .bind(name)
        .fetch_optional(pool)
        .await
}

pub async fn find_chapters_by_story_id(pool: &Pool<Postgres>, story_id: i64) -> Result<Vec<Chapter>, sqlx::Error> {
    sqlx::query_as::<_, Chapter>("SELECT id, story_id, title, key, content, created_at FROM chapter WHERE story_id = $1 ORDER BY key ASC")
        .bind(story_id)
        .fetch_all(pool)
        .await
}

pub async fn find_chapters_by_story_id_paged(
    pool: &Pool<Postgres>,
    story_id: i64,
    limit: i64,
    offset: i64,
) -> Result<Vec<Chapter>, sqlx::Error> {
     sqlx::query_as::<_, Chapter>(
        "SELECT id, story_id, title, key, content, created_at FROM chapter WHERE story_id = $1 ORDER BY key ASC LIMIT $2 OFFSET $3"
    )
    .bind(story_id)
    .bind(limit)
    .bind(offset)
    .fetch_all(pool)
    .await
}

pub async fn count_chapters_by_story_id(pool: &Pool<Postgres>, story_id: i64) -> Result<i64, sqlx::Error> {
    let row = sqlx::query("SELECT COUNT(*) FROM chapter WHERE story_id = $1")
        .bind(story_id)
        .fetch_one(pool)
        .await?;
    let count: i64 = row.get(0);
    Ok(count)
}

pub async fn find_chapter_by_id(pool: &Pool<Postgres>, id: i64) -> Result<Option<Chapter>, sqlx::Error> {
    sqlx::query_as::<_, Chapter>("SELECT id, story_id, title, key, content, created_at FROM chapter WHERE id = $1")
        .bind(id)
        .fetch_optional(pool)
        .await
}

pub async fn save_bookmark(pool: &Pool<Postgres>, story_id: i64, chapter_id: i64) -> Result<(), sqlx::Error> {
    sqlx::query(
        "INSERT INTO bookmarks (story_id, chapter_id) VALUES ($1, $2) ON CONFLICT (story_id) DO UPDATE SET chapter_id = $2"
    )
    .bind(story_id)
    .bind(chapter_id)
    .execute(pool)
    .await?;
    Ok(())
}

pub async fn delete_bookmark(pool: &Pool<Postgres>, id: i64) -> Result<(), sqlx::Error> {
    sqlx::query("DELETE FROM bookmarks WHERE id = $1")
        .bind(id)
        .execute(pool)
        .await?;
    Ok(())
}

pub async fn find_all_bookmarks(pool: &Pool<Postgres>) -> Result<Vec<Bookmark>, sqlx::Error> {
    sqlx::query_as::<_, Bookmark>("SELECT id, story_id, chapter_id, created_at FROM bookmarks ORDER BY id DESC")
        .fetch_all(pool)
        .await
}
