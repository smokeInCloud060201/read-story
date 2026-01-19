package com.example.readstory.story.controller;

import com.example.readstory.story.entity.Chapter;
import com.example.readstory.story.entity.Story;
import com.example.readstory.story.service.StoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;

@Controller
@RequiredArgsConstructor
public class ViewController {

    private final StoryService storyService;

    @GetMapping("/")
    public String index() {
        return "redirect:/docs/stories";
    }

    @GetMapping("/docs/stories")
    public String listStories(Model model) {
        List<Story> stories = storyService.findAllStories();
        List<com.example.readstory.story.entity.Bookmark> bookmarks = storyService.findAllBookmarks();
        model.addAttribute("stories", stories);
        model.addAttribute("bookmarks", bookmarks);
        return "stories";
    }

    @GetMapping("/docs/stories/{story-name}/chapters")
    public String listChapters(@PathVariable("story-name") String storyName,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            Model model) {
        Story story = storyService.findStoryByName(storyName);

        Pageable pageable = PageRequest.of(page, size);
        Page<Chapter> chapterPage = storyService.findChaptersByStoryId(story.getId(), pageable);

        model.addAttribute("story", story);
        model.addAttribute("chapters", chapterPage.getContent());
        model.addAttribute("page", chapterPage);
        model.addAttribute("currentPage", chapterPage.getNumber());
        model.addAttribute("totalPages", chapterPage.getTotalPages());
        model.addAttribute("totalItems", chapterPage.getTotalElements());
        model.addAttribute("pageSize", chapterPage.getSize());

        return "chapters";
    }

    @GetMapping("/docs/stories/{story-name}/chapters/{chapterId}")
    public String viewChapter(@PathVariable("story-name") String storyName,
            @PathVariable Long chapterId,
            Model model) {
        Story story = storyService.findStoryByName(storyName);
        Chapter chapter = storyService.findChapterById(chapterId);
        List<Chapter> allChapters = storyService.findChaptersByStoryId(story.getId());

        // Find previous and next chapters
        Chapter previousChapter = null;
        Chapter nextChapter = null;

        for (int i = 0; i < allChapters.size(); i++) {
            if (allChapters.get(i).getId().equals(chapterId)) {
                if (i > 0) {
                    previousChapter = allChapters.get(i - 1);
                }
                if (i < allChapters.size() - 1) {
                    nextChapter = allChapters.get(i + 1);
                }
                break;
            }
        }

        model.addAttribute("story", story);
        model.addAttribute("chapter", chapter);
        model.addAttribute("previousChapter", previousChapter);
        model.addAttribute("nextChapter", nextChapter);
        model.addAttribute("chapterIndex", allChapters.indexOf(chapter) + 1);
        model.addAttribute("totalChapters", allChapters.size());

        return "chapter";
    }
}
