package com.example.readstory.crawl.dto;

import lombok.Getter;

import java.util.List;

@Getter
public class MTCChaptersResponse {

    private List<MTCChapterResponse> data;

    @Getter
    public static class MTCChapterResponse {
        private String name;
        private String index;
    }
}
