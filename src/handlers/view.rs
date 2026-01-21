use actix_web::{get, web, HttpResponse, Responder};
use askama::Template;
use crate::db;
use crate::models::{Story, Chapter};
use sqlx::{Pool, Postgres, Row};

#[derive(Template)]
#[template(path = "stories.html")]
pub struct StoriesTemplate {
    pub stories: Vec<StoryView>,
    pub bookmarks: Vec<BookmarkView>,
}

pub struct StoryView {
    pub id: i64,
    pub name: String,
    pub title: String,
    pub created_at: String,
}

pub struct BookmarkView {
    pub id: i64,
    pub story_name: String,
    pub chapter_id: i64,
    pub chapter_title: String,
}

#[get("/")]
pub async fn index() -> impl Responder {
    HttpResponse::MovedPermanently()
        .insert_header(("Location", "/docs/stories"))
        .finish()
}

#[get("/docs/stories")]
pub async fn list_stories(pool: web::Data<Pool<Postgres>>) -> impl Responder {
    let stories_raw = db::find_all_stories(&pool).await.unwrap_or_default();
    let bookmarks_raw = db::find_all_bookmarks(&pool).await.unwrap_or_default();
    
    let stories = stories_raw.into_iter().map(|s| StoryView {
        id: s.id,
        name: s.name.clone(),
        title: s.title.unwrap_or(s.name),
        created_at: s.created_at.map(|d| d.format("%Y-%m-%d").to_string()).unwrap_or_default(),
    }).collect();

    let mut bookmarks = Vec::new();
    for b in bookmarks_raw {
        if let (Some(chapter_id), story_id) = (b.chapter_id, b.story_id) {
            let story_row = sqlx::query("SELECT name FROM stories WHERE id = $1")
                .bind(story_id)
                .fetch_optional(pool.get_ref())
                .await
                .unwrap_or(None);
            
            let chapter_row = sqlx::query("SELECT title FROM chapter WHERE id = $1")
                .bind(chapter_id)
                .fetch_optional(pool.get_ref())
                .await
                .unwrap_or(None);
            
            if let (Some(story_row), Some(chapter_row)) = (story_row, chapter_row) {
                bookmarks.push(BookmarkView {
                    id: b.id,
                    story_name: story_row.get("name"),
                    chapter_id,
                    chapter_title: chapter_row.get::<Option<String>, _>("title").unwrap_or_else(|| "Unknown".to_string()),
                });
            }
        }
    }

    let s = StoriesTemplate { stories, bookmarks };
    match s.render() {
        Ok(html) => HttpResponse::Ok().content_type("text/html").body(html),
        Err(e) => {
            log::error!("Template error: {}", e);
            HttpResponse::InternalServerError().finish()
        },
    }
}

pub struct ChapterView {
    pub id: i64,
    pub title: String,
    pub created_at: String,
}

#[derive(serde::Serialize)]
pub enum PageItem {
    Page(i64),
    Ellipsis,
}

#[derive(Template)]
#[template(path = "chapters.html")]
pub struct ChaptersTemplate {
    pub story: Story,
    pub chapters: Vec<ChapterView>,
    pub current_page: i64,
    pub total_pages: i64,
    pub total_elements: i64,
    pub page_size: i64,
    pub display_items: Vec<PageItem>,
}

#[get("/docs/stories/{story_name}/chapters")]
pub async fn list_chapters(
    pool: web::Data<Pool<Postgres>>,
    story_name: web::Path<String>,
    query: web::Query<PaginationParams>,
) -> impl Responder {
    let story_name = story_name.into_inner();
    let story = match db::find_story_by_name(&pool, &story_name).await {
        Ok(Some(s)) => s,
        _ => return HttpResponse::NotFound().finish(),
    };

    let page = query.page.unwrap_or(0);
    let size = query.size.unwrap_or(50);
    let offset = page * size;

    let chapters_raw = db::find_chapters_by_story_id_paged(&pool, story.id, size, offset).await.unwrap_or_default();
    let chapters = chapters_raw.into_iter().map(|c| ChapterView {
        id: c.id,
        title: c.title.unwrap_or_else(|| "Unknown".to_string()),
        created_at: c.created_at.map(|d| d.format("%Y-%m-%d").to_string()).unwrap_or_default(),
    }).collect();

    let total_elements = db::count_chapters_by_story_id(&pool, story.id).await.unwrap_or(0);
    let total_pages = if size > 0 { (total_elements as f64 / size as f64).ceil() as i64 } else { 0 };

    let mut display_items = Vec::new();
    if total_pages > 0 {
        let mut last_page = -1;
        for i in 0..total_pages {
            if i < 5 || i == total_pages - 1 || (i >= page - 2 && i <= page + 2) {
                if last_page != -1 && i > last_page + 1 {
                    display_items.push(PageItem::Ellipsis);
                }
                display_items.push(PageItem::Page(i));
                last_page = i;
            }
        }
    }

    let s = ChaptersTemplate {
        story,
        chapters,
        current_page: page,
        total_pages,
        total_elements,
        page_size: size,
        display_items,
    };

    match s.render() {
        Ok(html) => HttpResponse::Ok().content_type("text/html").body(html),
        Err(e) => {
            log::error!("Template error: {}", e);
            HttpResponse::InternalServerError().finish()
        },
    }
}

#[derive(serde::Deserialize)]
pub struct PaginationParams {
    pub page: Option<i64>,
    pub size: Option<i64>,
}

#[derive(Template)]
#[template(path = "chapter.html")]
pub struct ChapterTemplate {
    pub story: Story,
    pub chapter: Chapter,
    pub previous_chapter: Option<Chapter>,
    pub next_chapter: Option<Chapter>,
    pub chapter_index: usize,
    pub total_chapters: usize,
}

#[get("/docs/stories/{story_name}/chapters/{chapter_id}")]
pub async fn view_chapter(
    pool: web::Data<Pool<Postgres>>,
    path: web::Path<(String, i64)>,
) -> impl Responder {
    let (story_name, chapter_id) = path.into_inner();
    let story = match db::find_story_by_name(&pool, &story_name).await {
        Ok(Some(s)) => s,
        _ => return HttpResponse::NotFound().finish(),
    };

    let chapter = match db::find_chapter_by_id(&pool, chapter_id).await {
        Ok(Some(c)) => c,
        _ => return HttpResponse::NotFound().finish(),
    };

    let all_chapters = db::find_chapters_by_story_id(&pool, story.id).await.unwrap_or_default();
    
    let mut previous_chapter = None;
    let mut next_chapter = None;
    let mut chapter_index = 0;

    for (i, c) in all_chapters.iter().enumerate() {
        if c.id == chapter_id {
            chapter_index = i + 1;
            if i > 0 {
                previous_chapter = Some(all_chapters[i - 1].clone());
            }
            if i < all_chapters.len() - 1 {
                next_chapter = Some(all_chapters[i + 1].clone());
            }
            break;
        }
    }

    let s = ChapterTemplate {
        story,
        chapter,
        previous_chapter,
        next_chapter,
        chapter_index,
        total_chapters: all_chapters.len(),
    };

    match s.render() {
        Ok(html) => HttpResponse::Ok().content_type("text/html").body(html),
        Err(e) => {
            log::error!("Template error: {}", e);
            HttpResponse::InternalServerError().finish()
        },
    }
}
