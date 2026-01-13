package com.example.readstory.crawl.service.impl;

import com.example.readstory.common.dto.RetrySpec;
import com.example.readstory.common.service.HtmlParseService;
import com.example.readstory.common.service.RetryService;
import com.example.readstory.crawl.service.CrawlService;
import com.example.readstory.crawl.service.CrawlStrategy;
import com.example.readstory.story.dto.ChapterDTO;
import com.example.readstory.story.dto.ChapterTask;
import com.example.readstory.story.entity.Chapter;
import com.example.readstory.story.entity.Story;
import com.example.readstory.story.repository.ChapterRepository;
import com.example.readstory.story.repository.StoryRepository;
import com.example.readstory.util.StrategyUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CrawlServiceImpl implements CrawlService {

    private final StoryRepository storyRepository;
    private final ChapterRepository chapterRepository;
    private final RetryService retryService;
    private final HtmlParseService htmlParseService;
    private final ExecutorService virtualThreadExecutor;

    private static final int MAX_CONCURRENCY = 10;
    private static final int CHAPTER_PERSIST_BATCH_SIZE = 100;
    private static final int CHAPTER_PROCESS_BATCH_SIZE = 400;

    @Override
    @Async
    public void crawlStory(String url) {
        if (StringUtils.isBlank(url)) {
            throw new IllegalArgumentException("URL is blank");
        }

        CrawlStrategy strategy = StrategyUtil.getCrawlStrategy(url);

        log.info("#CrawlService: Using strategy {} for URL {}", strategy.getClass().getSimpleName(), url);

        Story storyInfo = strategy.parseStory(url, htmlParseService);
        final String storyName = storyInfo.getName();

        final Optional<Story> optionalStory = storyRepository.findStoryByName(storyName);
        final Story story;
        if (optionalStory.isPresent()) {
            log.info("#CrawlService: Story {} already exists", storyName);
            story = optionalStory.get();
        } else {
            story = storyRepository.save(storyInfo);
        }

        List<ChapterTask> allTasks = strategy.parseChapterTasks(url, story, htmlParseService);
        List<ChapterTask> tasksToProcess = filterExistingChapters(allTasks, story);

        if (tasksToProcess.isEmpty()) {
            log.info("#CrawlService: No new chapters to crawl for {}", storyName);
            return;
        }

        processInBatches(strategy, story, tasksToProcess);
    }

    private List<ChapterTask> filterExistingChapters(List<ChapterTask> tasks, Story story) {
        if (tasks.isEmpty()) return List.of();

        Set<Integer> existingKeys = chapterRepository.findExistingKeys(story.getId(), tasks.stream().map(ChapterTask::chapterKey).collect(Collectors.toSet()));

        return tasks.stream().filter(t -> !existingKeys.contains(t.chapterKey())).toList();
    }

    private void processInBatches(CrawlStrategy strategy, Story story, List<ChapterTask> tasks) {
        final RetrySpec retrySpec = RetrySpec.of(5, 500, 8_000);
        List<ChapterDTO.ChapterResponse> responses = new ArrayList<>();

        for (int i = 0; i < tasks.size(); i += CHAPTER_PROCESS_BATCH_SIZE) {
            int end = Math.min(i + CHAPTER_PROCESS_BATCH_SIZE, tasks.size());
            List<ChapterTask> batch = tasks.subList(i, end);

            log.info("Processing batch {}/{} with {} items", (i / CHAPTER_PROCESS_BATCH_SIZE) + 1, (tasks.size() + CHAPTER_PROCESS_BATCH_SIZE - 1) / CHAPTER_PROCESS_BATCH_SIZE, batch.size());
            processChapterBatch(strategy, batch, story, retrySpec, responses);

            if (end < tasks.size()) {
                try {
                    log.info("Sleeping for 10 minutes before next batch...");
                    Thread.sleep(10 * 60 * 1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.error("Sleep interrupted between batches", e);
                    break;
                }
            }
        }
    }

    private void processChapterBatch(CrawlStrategy strategy, List<ChapterTask> tasks, Story story, RetrySpec retrySpec, List<ChapterDTO.ChapterResponse> responses) {
        CompletionService<Chapter> completionService = new ExecutorCompletionService<>(virtualThreadExecutor);
        Semaphore semaphore = new Semaphore(MAX_CONCURRENCY);

        for (ChapterTask task : tasks) {
            completionService.submit(() -> {
                try {
                    semaphore.acquire();
                    return retryService.execute(() -> strategy.parseChapter(story, task, htmlParseService), retrySpec);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return null;
                } catch (Exception e) {
                    log.error("Failed chapter {}", task.rawKey(), e);
                    return null;
                } finally {
                    semaphore.release();
                }
            });
        }

        List<Chapter> buffer = new ArrayList<>(CHAPTER_PERSIST_BATCH_SIZE);
        for (int i = 0; i < tasks.size(); i++) {
            try {
                Chapter chapter = completionService.take().get();
                if (chapter != null) {
                    buffer.add(chapter);
                }

                if (buffer.size() == CHAPTER_PERSIST_BATCH_SIZE) {
                    persistBatch(buffer, responses);
                    buffer.clear();
                }
            } catch (Exception e) {
                log.error("Error consuming chapter result", e);
            }
        }

        if (!buffer.isEmpty()) {
            persistBatch(buffer, responses);
        }
    }

    private void persistBatch(List<Chapter> batch, List<ChapterDTO.ChapterResponse> responses) {
        log.info("Saving batch of {} chapters", batch.size());
        List<Chapter> saved = chapterRepository.saveAll(batch);
        saved.forEach(c -> responses.add(ChapterDTO.ChapterResponse.builder().id(c.getId()).title(c.getTitle()).build()));
    }
}
