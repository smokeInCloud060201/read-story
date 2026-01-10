package com.example.readstory.story.entity;

import com.example.readstory.common.entities.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "chapter")
@Getter
@Setter
public class Chapter extends BaseEntity {

    @Column
    private String title;

    @Column(unique = true)
    private Integer key;

    @Column(columnDefinition = "TEXT")
    private String content;

    @ManyToOne
    @JoinColumn(name = "story_id")
    private Story story;
}
