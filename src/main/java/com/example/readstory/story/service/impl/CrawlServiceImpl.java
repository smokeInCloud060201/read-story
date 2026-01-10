package com.example.readstory.story.service.impl;

import com.example.readstory.common.dto.RetrySpec;
import com.example.readstory.common.service.HtmlParseService;
import com.example.readstory.common.service.RetryService;
import com.example.readstory.story.dto.ChapterDTO;
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
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;

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
    private static final int BATCH_SIZE = 200;

    @Override
    public ChapterDTO.StoryResponse crawlStory(String storyName) {
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

        List<ChapterDTO.ChapterResponse> chapters = getChapters(String.format("%s/ajax.php?type=chapter_option&data=%s", baseUrl, storyId), story);

        return ChapterDTO.StoryResponse.builder().chapterResponseList(chapters).size(Optional.ofNullable(chapters).map(List::size).orElse(0)).build();
    }

    @Override
    @Retryable
    public List<ChapterDTO.ChapterResponse> getChapters(String url, Story story) {
        final Document document = htmlParseService.getDocument(url);
        final Elements chapterElements = htmlParseService.getElements(document, CHAPTER_LIST_QUERY_SELECTOR);

        final RetrySpec retrySpec = RetrySpec.of(5, 500, 8_000);
        final Semaphore semaphore = new Semaphore(MAX_CONCURRENCY);

        CompletionService<Chapter> completionService = new ExecutorCompletionService<>(virtualThreadExecutor);

        for (Element el : chapterElements) {
            completionService.submit(() -> {
                String chapterKey = el.attr("value");

                try {
                    semaphore.acquire();
                    return retryService.execute(() -> crawlSingleChapter(story, chapterKey), retrySpec);

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return null;

                } catch (Exception e) {
                    log.error("Failed chapter {}", chapterKey, e);
                    return null;

                } finally {
                    semaphore.release();
                }
            });
        }

        List<Chapter> buffer = new ArrayList<>(BATCH_SIZE);
        List<ChapterDTO.ChapterResponse> responses = new ArrayList<>();

        int totalTasks = chapterElements.size();

        // consume completed tasks
        for (int i = 0; i < totalTasks; i++) {
            try {
                Future<Chapter> future = completionService.take(); // waits for ANY finished task
                Chapter chapter = future.get();

                if (chapter != null) {
                    buffer.add(chapter);
                }

                if (buffer.size() == BATCH_SIZE) {
                    persistBatch(buffer, responses);
                    buffer.clear();
                }

            } catch (Exception e) {
                log.error("Error consuming chapter result", e);
            }
        }

        // persist remaining
        if (!buffer.isEmpty()) {
            persistBatch(buffer, responses);
        }

        return responses;
    }


    private Chapter crawlSingleChapter(Story story, String key) {
        Integer chapterKey = Integer.parseInt(key.split("-")[1]);

        final Optional<Chapter> optionalChapter = chapterRepository.findChapterByKeyAndStory_Id(chapterKey, story.getId());

        if (optionalChapter.isPresent()) {
            return optionalChapter.get();
        }

        String chapterUrl = baseUrl + "/" + story.getName() + "/" + key;

        log.info("#CrawlService: Crawling {}", chapterUrl);

        Document chapterDoc = htmlParseService.getDocument(chapterUrl);
        Element container = htmlParseService.getElementById(chapterDoc.body(), "chapter-big-container");

        String title = htmlParseService.getFirstElement(container, "a.chapter-title").attr("title");

        Element contentEl = htmlParseService.getFirstElement(container, "#chapter-c");
        String contentHtml = contentEl.html();

        Chapter chapter = new Chapter();
        chapter.setTitle(title);
        chapter.setContent(contentHtml);
        chapter.setStory(story);
        chapter.setKey(chapterKey);

        return chapter;
    }

    private void persistBatch(List<Chapter> batch, List<ChapterDTO.ChapterResponse> responses) {
        log.info("Saving batch of {} chapters", batch.size());

        List<Chapter> saved = chapterRepository.saveAll(batch);

        saved.forEach(c -> responses.add(ChapterDTO.ChapterResponse.builder().id(c.getId()).title(c.getTitle()).build()));
    }
}
