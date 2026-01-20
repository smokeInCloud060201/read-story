use serde::{Deserialize, Serialize};
use sqlx::Type;

#[derive(Debug, Serialize, Deserialize, Clone, Copy, Type, PartialEq)]
#[sqlx(type_name = "VARCHAR")]
pub enum Source {
    TRUYEN_FULL,
    MTC,
}

impl From<String> for Source {
    fn from(s: String) -> Self {
        match s.as_str() {
            "TRUYEN_FULL" => Source::TRUYEN_FULL,
            "MTC" => Source::MTC,
            _ => Source::TRUYEN_FULL,
        }
    }
}

impl From<Source> for String {
    fn from(s: Source) -> Self {
        match s {
            Source::TRUYEN_FULL => "TRUYEN_FULL".to_string(),
            Source::MTC => "MTC".to_string(),
        }
    }
}
