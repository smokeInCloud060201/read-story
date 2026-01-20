use serde::{Deserialize, Serialize};
use sqlx::FromRow;
use chrono::{DateTime, Utc};

#[derive(Debug, Serialize, Deserialize, FromRow, Clone)]
pub struct Bookmark {
    pub id: i64,
    pub story_id: i64,
    pub chapter_id: Option<i64>,
    pub created_at: Option<DateTime<Utc>>,
}
