package com.example.readstory.story.repository;

import com.example.readstory.story.entity.Bookmark;
import com.example.readstory.story.entity.Story;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BookmarkRepository extends JpaRepository<Bookmark, Long> {
    Optional<Bookmark> findByStory(Story story);

    Optional<Bookmark> findByStoryId(Long storyId);
}
