package com.example.readstory.crawl.service.impl;

import com.example.readstory.common.annotation.Source;
import com.example.readstory.common.annotation.Strategy;
import com.example.readstory.common.service.HtmlParseService;
import com.example.readstory.crawl.service.CrawlStrategy;
import com.example.readstory.story.dto.ChapterTask;
import com.example.readstory.story.entity.Chapter;
import com.example.readstory.story.entity.Story;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.List;
import java.util.Objects;

@Strategy(name = "TFStrategy", description = "The strategy use to crawl story in https://truyenfull.vision", source = Source.TRUYEN_FULL, baseHost = "truyenfull.vision")
@Slf4j
public class TFCrawlStrategy implements CrawlStrategy {

    private static final String BASE_URL = "https://truyenfull.vision";
    private static final String CHAPTER_LIST_QUERY_SELECTOR = "select.chapter_jump option";

    @Override
    public boolean supports(String url) {
        return url.startsWith(BASE_URL);
    }

    @Override
    public Story parseStory(String url, HtmlParseService htmlParseService) {
        // Extract story name from URL
        // Let's assume URL is like https://truyenfull.vision/story-name
        String storyName = url.substring(BASE_URL.length() + 1);

        Story s = new Story();
        s.setName(storyName);
        return s;
    }

    @Override
    public List<ChapterTask> parseChapterTasks(String url, Story story, HtmlParseService htmlParseService) {
        final Document document = htmlParseService.getDocument(url);
        final Element storyIdEle = htmlParseService.getElementById(document.body(), "truyen-id");
        final String storyId = storyIdEle.attr("value");

        final String ajaxUrl = String.format("%s/ajax.php?type=chapter_option&data=%s", BASE_URL, storyId);
        final Document ajaxDoc = htmlParseService.getDocument(ajaxUrl);
        final Elements chapterElements = htmlParseService.getElements(ajaxDoc, CHAPTER_LIST_QUERY_SELECTOR);

        return chapterElements.stream().map(el -> {
            String rawKey = el.attr("value");
            try {
                int chapterKey = Integer.parseInt(rawKey.split("-")[1]);
                return new ChapterTask(el.text(), chapterKey, rawKey);
            } catch (Exception e) {
                log.warn("Invalid chapter key: {}", rawKey);
                return null;
            }
        }).filter(Objects::nonNull).toList();
    }

    @Override
    public Chapter parseChapter(Story story, ChapterTask task, HtmlParseService htmlParseService) {
        String chapterUrl = BASE_URL + "/" + story.getName() + "/" + task.rawKey();
        log.info("#TFCrawlStrategy: Crawling {}", chapterUrl);

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
}
