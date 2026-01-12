package com.example.readstory.story.controller;

import com.example.readstory.common.dto.BaseResponse;
import com.example.readstory.common.utils.BaseResponseMapper;
import com.example.readstory.story.service.CrawlService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/crawl")
public class CrawlController {

    private final CrawlService crawlService;

    @PostMapping("/{story_name}")
    public BaseResponse<Void> crawlStory(@PathVariable("story_name") String storyName) {
        crawlService.crawlStory(storyName);
        return BaseResponseMapper.toSuccess(null);
    }
}
