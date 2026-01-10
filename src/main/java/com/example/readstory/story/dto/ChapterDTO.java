package com.example.readstory.story.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.ZonedDateTime;
import java.util.List;

public interface ChapterDTO {

    @Getter
    @Builder
     class StoryResponse {
        private String storyName;
        private int size;
        private ZonedDateTime createdAt;
        private List<ChapterResponse> chapterResponseList;
    }

    @Getter
    @Builder
    class ChapterResponse {
        private long id;
        private String title;
        private String content;
    }
}
