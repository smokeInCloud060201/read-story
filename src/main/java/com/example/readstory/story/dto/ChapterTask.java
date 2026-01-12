package com.example.readstory.story.dto;

import org.jsoup.nodes.Element;

public record ChapterTask(Element element, int chapterKey, String rawKey) {}