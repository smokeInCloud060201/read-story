package com.example.readstory.story.service;

import com.example.readstory.common.dto.BaseResponse;
import com.example.readstory.story.dto.ChapterDTO;
import com.example.readstory.story.entity.Chapter;
import com.example.readstory.story.entity.Story;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface StoryService {
    BaseResponse<ChapterDTO.StoryResponse> getStoryByName(String name);

    List<Story> findAllStories();

    Story findStoryByName(String storyName);

    List<Chapter> findChaptersByStoryId(Long storyId);

    Page<Chapter> findChaptersByStoryId(Long storyId, Pageable pageable);

    Chapter findChapterById(Long chapterId);
}
