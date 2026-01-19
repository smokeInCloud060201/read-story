package com.example.readstory.story.entity;

import com.example.readstory.common.entities.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "bookmarks")
@Getter
@Setter
@NoArgsConstructor
public class Bookmark extends BaseEntity {

    @OneToOne
    @JoinColumn(name = "story_id", unique = true)
    private Story story;

    @ManyToOne
    @JoinColumn(name = "chapter_id")
    private Chapter chapter;

    public Bookmark(Story story, Chapter chapter) {
        this.story = story;
        this.chapter = chapter;
    }
}
