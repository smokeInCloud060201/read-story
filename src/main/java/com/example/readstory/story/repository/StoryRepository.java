package com.example.readstory.story.repository;

import com.example.readstory.story.entity.Story;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StoryRepository extends JpaRepository<Story, Long> {

    Optional<Story> findStoryByName(String name);
}
