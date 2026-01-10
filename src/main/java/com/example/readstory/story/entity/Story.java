package com.example.readstory.story.entity;

import com.example.readstory.common.entities.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Entity
@Table(name = "stories")
@Getter
@Setter
public class Story extends BaseEntity {

    @Column(unique = true)
    private String name;

    @OneToMany(fetch = FetchType.LAZY, cascade = CascadeType.ALL, mappedBy = "story")
    List<Chapter> chapterList;

}
