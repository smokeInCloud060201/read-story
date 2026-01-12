package com.example.readstory.story.service;

import com.example.readstory.story.dto.ChapterDTO;
import com.example.readstory.story.entity.Story;

import java.util.List;

public interface CrawlService {

    void crawlStory(String storyName);
    List<ChapterDTO.ChapterResponse> getChapters(String url, Story story);
}
