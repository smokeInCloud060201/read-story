use serde::{Deserialize, Serialize};
use sqlx::FromRow;
use chrono::{DateTime, Utc};

#[derive(Debug, Serialize, Deserialize, FromRow, Clone)]
pub struct Chapter {
    pub id: i64,
    pub story_id: i64,
    pub title: Option<String>,
    pub key: Option<i32>,
    pub content: Option<String>,
    pub created_at: Option<DateTime<Utc>>,
}
