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
    private final com.example.readstory.story.repository.BookmarkRepository bookmarkRepository;

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

    @Override
    public void saveBookmark(Long storyId, Long chapterId) {
        Story story = storyRepository.findById(storyId).orElseThrow(() -> new RuntimeException("Story not found"));
        Chapter chapter = chapterRepository.findById(chapterId)
                .orElseThrow(() -> new RuntimeException("Chapter not found"));

        com.example.readstory.story.entity.Bookmark bookmark = bookmarkRepository.findByStoryId(storyId)
                .orElse(new com.example.readstory.story.entity.Bookmark());

        bookmark.setStory(story);
        bookmark.setChapter(chapter);
        bookmarkRepository.save(bookmark);
    }

    @Override
    public void deleteBookmark(Long bookmarkId) {
        bookmarkRepository.deleteById(bookmarkId);
    }

    @Override
    public List<com.example.readstory.story.entity.Bookmark> findAllBookmarks() {
        return bookmarkRepository.findAll();
    }

}
