-- Create stories table
CREATE TABLE IF NOT EXISTS stories (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) UNIQUE NOT NULL,
    title VARCHAR(255),
    source VARCHAR(50),
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

-- Create chapter table
CREATE TABLE IF NOT EXISTS chapter (
    id BIGSERIAL PRIMARY KEY,
    story_id BIGINT REFERENCES stories(id) ON DELETE CASCADE,
    title VARCHAR(255),
    key INTEGER,
    content TEXT,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_chapter_story_key UNIQUE (story_id, key)
);

-- Create bookmarks table
CREATE TABLE IF NOT EXISTS bookmarks (
    id BIGSERIAL PRIMARY KEY,
    story_id BIGINT UNIQUE REFERENCES stories(id) ON DELETE CASCADE,
    chapter_id BIGINT REFERENCES chapter(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);
