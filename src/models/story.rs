use serde::{Deserialize, Serialize};
use sqlx::FromRow;
use chrono::{DateTime, Utc};

#[derive(Debug, Serialize, Deserialize, FromRow, Clone)]
pub struct Story {
    pub id: i64,
    pub name: String,
    pub title: Option<String>,
    pub source: Option<String>,
    pub created_at: Option<DateTime<Utc>>,
}
