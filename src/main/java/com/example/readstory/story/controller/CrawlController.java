package com.example.readstory.story.controller;

import com.example.readstory.common.dto.BaseResponse;
import com.example.readstory.common.utils.BaseResponseMapper;
import com.example.readstory.crawl.service.CrawlService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/crawl")
public class CrawlController {

    private final CrawlService crawlService;

    @PostMapping
    public BaseResponse<Void> crawlStoryByName(@RequestParam("url") String url) {
        crawlService.crawlStory(url);
        return BaseResponseMapper.toSuccess(null);
    }
}
