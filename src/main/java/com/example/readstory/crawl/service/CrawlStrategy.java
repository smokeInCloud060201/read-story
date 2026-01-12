package com.example.readstory.crawl.service;

import com.example.readstory.common.service.HtmlParseService;
import com.example.readstory.story.dto.ChapterTask;
import com.example.readstory.story.entity.Chapter;
import com.example.readstory.story.entity.Story;
import java.util.List;

public interface CrawlStrategy {

    boolean supports(String url);

    Story parseStory(String url, HtmlParseService htmlParseService);

    List<ChapterTask> parseChapterTasks(String url, Story story, HtmlParseService htmlParseService);

    Chapter parseChapter(Story story, ChapterTask task, HtmlParseService htmlParseService);
}
