package com.example.readstory.story.repository;

import com.example.readstory.story.entity.Chapter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChapterRepository extends JpaRepository<Chapter, Long> {

    Optional<Chapter> findChapterByKeyAndStory_Id(Integer key, Long storyId);

    List<Chapter> findByStoryIdOrderByKeyAsc(Long storyId);

    @Query(value = """
            SELECT *
            FROM chapter c
            WHERE c.story_id = :storyId
            ORDER BY c."key" ASC
            """, countQuery = """
            SELECT COUNT(*)
            FROM chapter c
            WHERE c.story_id = :storyId
            """, nativeQuery = true)
    Page<Chapter> getChaptersByStoryId(@Param("storyId") Long storyId, Pageable pageable);
}
