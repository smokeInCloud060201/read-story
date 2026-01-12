package com.example.readstory.story.service.impl;

import com.example.readstory.common.dto.RetrySpec;
import com.example.readstory.common.service.HtmlParseService;
import com.example.readstory.common.service.RetryService;
import com.example.readstory.story.dto.ChapterDTO;
import com.example.readstory.story.dto.ChapterTask;
import com.example.readstory.story.entity.Chapter;
import com.example.readstory.story.entity.Story;
import com.example.readstory.story.repository.ChapterRepository;
import com.example.readstory.story.repository.StoryRepository;
import com.example.readstory.story.service.CrawlService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
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
    private final HtmlParseService htmlParseService;
    private final RetryService retryService;
    private final ExecutorService virtualThreadExecutor;

    @Value("${story.base-url}")
    private String baseUrl;

    private static final String CHAPTER_LIST_QUERY_SELECTOR = "select.chapter_jump option";
    private static final int MAX_CONCURRENCY = 10;
    private static final int CHAPTER_PERSIST_BATCH_SIZE = 100;
    private static final int CHAPTER_PROCESS_BATCH_SIZE = 400;

    @Override
    @Async
    public void crawlStory(String storyName) {
        if (StringUtils.isBlank(storyName)) {
            throw new IllegalArgumentException("storyName is blank");
        }

        final String storyUrl = baseUrl + "/" + storyName;
        final Document document = htmlParseService.getDocument(storyUrl);
        final Element body = document.body();
        final Element storyIdEle = htmlParseService.getElementById(body, "truyen-id");
        final String storyId = storyIdEle.attr("value");

        final Optional<Story> optionalStory = storyRepository.findStoryByName(storyName);
        final Story story;
        if (optionalStory.isPresent()) {
            log.info("#CrawlService: Store {} is already exits", storyName);
            story = optionalStory.get();
        } else {
            Story s = new Story();
            s.setName(storyName);
            story = storyRepository.save(s);
        }

        getChapters(String.format("%s/ajax.php?type=chapter_option&data=%s", baseUrl, storyId), story);
    }

    @Override
    public List<ChapterDTO.ChapterResponse> getChapters(String url, Story story) {
        final Document document = htmlParseService.getDocument(url);
        final Elements chapterElements = htmlParseService.getElements(document, CHAPTER_LIST_QUERY_SELECTOR);
        final RetrySpec retrySpec = RetrySpec.of(5, 500, 8_000);

        final List<ChapterTask> chaptersToProcess = getProcessTask(chapterElements, story);

        if (chaptersToProcess.isEmpty()) {
            return List.of();
        }

        List<ChapterDTO.ChapterResponse> responses = new ArrayList<>();
        List<List<ChapterTask>> batches = new ArrayList<>();

        for (int i = 0; i < chaptersToProcess.size(); i += CHAPTER_PROCESS_BATCH_SIZE) {
            int end = Math.min(i + CHAPTER_PROCESS_BATCH_SIZE, chaptersToProcess.size());
            batches.add(chaptersToProcess.subList(i, end));
        }

        log.info("Total chapters to process: {}. Split into {} batches.", chaptersToProcess.size(), batches.size());

        for (int i = 0; i < batches.size(); i++) {
            List<ChapterTask> batch = batches.get(i);
            log.info("Processing batch {}/{} with {} items", i + 1, batches.size(), batch.size());

            processChapterBatch(batch, story, retrySpec, responses);

            // Sleep if there are more batches
            if (i < batches.size() - 1) {
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

        return responses;
    }

    private void processChapterBatch(List<ChapterTask> tasks, Story story, RetrySpec retrySpec, List<ChapterDTO.ChapterResponse> responses) {
        CompletionService<Chapter> completionService = getChapterCompletionService(story, tasks, retrySpec);

        List<Chapter> buffer = new ArrayList<>(CHAPTER_PERSIST_BATCH_SIZE);
        int totalTasks = tasks.size();

        for (int i = 0; i < totalTasks; i++) {
            try {
                // Wait for completion
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

    private CompletionService<Chapter> getChapterCompletionService(Story story, List<ChapterTask> tasks, RetrySpec retrySpec) {

        Semaphore semaphore = new Semaphore(MAX_CONCURRENCY);

        CompletionService<Chapter> completionService = new ExecutorCompletionService<>(virtualThreadExecutor);

        for (ChapterTask task : tasks) {
            completionService.submit(() -> {
                try {
                    semaphore.acquire();
                    return retryService.execute(() -> crawlSingleChapter(story, task), retrySpec);
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
        return completionService;
    }

    private Chapter crawlSingleChapter(Story story, ChapterTask task) {

        String chapterUrl = baseUrl + "/" + story.getName() + "/" + task.rawKey();

        log.info("#CrawlService: Crawling {}", chapterUrl);

        Document chapterDoc = htmlParseService.getDocument(chapterUrl);
        Element container = htmlParseService.getElementById(chapterDoc.body(), "chapter-big-container");

        String title = htmlParseService.getFirstElement(container, "a.chapter-title").attr("title");

        String contentHtml = htmlParseService.getFirstElement(container, "#chapter-c").html();

        Chapter chapter = new Chapter();
        chapter.setTitle(title);
        chapter.setContent(contentHtml);
        chapter.setStory(story);
        chapter.setKey(task.chapterKey());

        return chapter;
    }

    private void persistBatch(List<Chapter> batch, List<ChapterDTO.ChapterResponse> responses) {
        log.info("Saving batch of {} chapters", batch.size());
        List<Chapter> saved = chapterRepository.saveAll(batch);
        saved.forEach(c -> responses.add(ChapterDTO.ChapterResponse.builder().id(c.getId()).title(c.getTitle()).build()));
    }

    private List<ChapterTask> getProcessTask(Elements chapterElements, Story story) {
        List<ChapterTask> tasks = chapterElements.stream().map(el -> {
            String rawKey = el.attr("value");
            try {
                int chapterKey = Integer.parseInt(rawKey.split("-")[1]);
                return new ChapterTask(el, chapterKey, rawKey);
            } catch (Exception e) {
                log.warn("Invalid chapter key: {}", rawKey);
                return null;
            }
        }).filter(Objects::nonNull).toList();

        if (tasks.isEmpty()) {
            return List.of();
        }

        Set<Integer> existingKeys = chapterRepository.findExistingKeys(story.getId(), tasks.stream().map(ChapterTask::chapterKey).collect(Collectors.toSet()));

        return tasks.stream().filter(t -> !existingKeys.contains(t.chapterKey())).toList();
    }
}
