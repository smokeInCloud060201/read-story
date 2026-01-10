package com.example.readstory.story.service.impl;

import com.example.readstory.common.dto.BaseResponse;
import com.example.readstory.story.dto.ChapterDTO;
import com.example.readstory.story.entity.Chapter;
import com.example.readstory.story.entity.Story;
import com.example.readstory.story.repository.ChapterRepository;
import com.example.readstory.story.repository.StoryRepository;
import com.example.readstory.story.service.StoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class StoryServiceImpl implements StoryService {

    private final StoryRepository storyRepository;
    private final ChapterRepository chapterRepository;

    @Override
    public BaseResponse<ChapterDTO.StoryResponse> getStoryByName(String name) {
        return null;
    }

    @Override
    public List<Story> findAllStories() {
        return storyRepository.findAll();
    }

    @Override
    public Story findStoryByName(String storyName) {
        return storyRepository.findStoryByName(storyName).orElse(null);
    }

    @Override
    public List<Chapter> findChaptersByStoryId(Long storyId) {
        return chapterRepository.findByStoryIdOrderByKeyAsc(storyId);
    }

    @Override
    public Page<Chapter> findChaptersByStoryId(Long storyId, Pageable pageable) {
        return chapterRepository.getChaptersByStoryId(storyId, pageable);
    }

    @Override
    public Chapter findChapterById(Long chapterId) {
        return chapterRepository.findById(chapterId).orElse(null);
    }

}
