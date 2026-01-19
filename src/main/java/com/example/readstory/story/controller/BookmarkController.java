package com.example.readstory.story.controller;

import com.example.readstory.story.entity.Bookmark;
import com.example.readstory.story.service.StoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/bookmarks")
@RequiredArgsConstructor
public class BookmarkController {

    private final StoryService storyService;

    @PostMapping
    public ResponseEntity<Void> bookmarkChapter(@RequestParam Long storyId, @RequestParam Long chapterId) {
        storyService.saveBookmark(storyId, chapterId);
        return ResponseEntity.ok().build();
    }

    @GetMapping
    public ResponseEntity<List<Bookmark>> listBookmarks() {
        return ResponseEntity.ok(storyService.findAllBookmarks());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteBookmark(@PathVariable Long id) {
        storyService.deleteBookmark(id);
        return ResponseEntity.ok().build();
    }
}
