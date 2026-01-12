package com.example.readstory.crawl.service.impl;

import com.example.readstory.common.annotation.Source;
import com.example.readstory.common.annotation.Strategy;
import com.example.readstory.common.service.HtmlParseService;
import com.example.readstory.crawl.service.CrawlStrategy;
import com.example.readstory.story.dto.ChapterTask;
import com.example.readstory.story.entity.Chapter;
import com.example.readstory.story.entity.Story;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Strategy(name = "MTCStrategy", description = "The strategy use to crawl story in https://metruyencv.com", source = Source.MTC, baseHost = "metruyencv.com")
@Slf4j
public class MTCCrawlStrategy implements CrawlStrategy {

    private static final String BASE_URL = "https://metruyencv.com";

    @Override
    public boolean supports(String url) {
        return url.startsWith(BASE_URL);
    }

    @Override
    public Story parseStory(String url, HtmlParseService htmlParseService) {
        String storySlug = url.substring(url.lastIndexOf("/") + 1);

        final Document document = htmlParseService.getDocument(url);
        String title = document.select("h1").text();
        if (title.isEmpty()) {
            title = storySlug;
        }

        Story s = new Story();
        s.setName(storySlug);
        s.setTitle(title);
        s.setSource(Source.MTC);
        return s;
    }

    @Override
    public List<ChapterTask> parseChapterTasks(String url, Story story, HtmlParseService htmlParseService) {
        final Document document = htmlParseService.getDocument(url);
        String html = document.html();

        // Extract book_id from window.bookData = { "book": { "id": 136693, ... } }
        Pattern pattern = Pattern.compile("\"id\":\\s*(\\d+)");
        Matcher matcher = pattern.matcher(html);
        if (!matcher.find()) {
            log.error("Could not find book_id in page: {}", url);
            return List.of();
        }
        String bookId = matcher.group(1);
        log.info("#MTCCrawlStrategy: Found book_id {} for {}", bookId, story.getName());

        String apiUrl = String.format(
                "https://backend.metruyencv.com/api/chapters?filter[book_id]=%s&filter[type]=published", bookId);

        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.error("Failed to fetch chapters from API: {}, status: {}", apiUrl, response.statusCode());
                return List.of();
            }

            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(response.body());
            JsonNode data = root.get("data");

            List<ChapterTask> tasks = new ArrayList<>();
            if (data.isArray()) {
                for (JsonNode node : data) {
                    String name = node.get("name").asText();
                    int index = node.get("index").asInt();
                    String id = node.get("id").asText();
                    tasks.add(new ChapterTask(name, index, id));
                }
            }
            return tasks;
        } catch (Exception e) {
            log.error("Error fetching chapter tasks for {}", story.getName(), e);
            return List.of();
        }
    }

    @Override
    public Chapter parseChapter(Story story, ChapterTask task, HtmlParseService htmlParseService) {
        String chapterUrl = String.format("%s/truyen/%s/chuong-%d", BASE_URL, story.getName(), task.chapterKey());
        log.info("#MTCCrawlStrategy: Crawling {}", chapterUrl);

        try {
            Document doc = htmlParseService.getDocument(chapterUrl);
            String html = doc.html();

            // Extract content from window.chapterData = { chapter: { ..., content: "..." }
            // }
            Pattern pattern = Pattern.compile("\"content\":\\s*\"([^\"]+)\"");
            Matcher matcher = pattern.matcher(html);

            if (!matcher.find()) {
                log.error("Could not find encoded content in chapter page: {}", chapterUrl);
                return null;
            }

            String encodedContent = matcher.group(1);
            String decodedContent = decrypt(encodedContent);

            Chapter chapter = new Chapter();
            chapter.setTitle(task.name());
            chapter.setContent(decodedContent);
            chapter.setStory(story);
            chapter.setKey(task.chapterKey());

            return chapter;
        } catch (Exception e) {
            log.error("Error crawling chapter: {}", chapterUrl, e);
            return null;
        }
    }

    private String decrypt(String cipherText) throws Exception {
        String keyStr = "LRKjYstrin[ygSmQ";
        byte[] cipherBytes = Base64.getDecoder().decode(cipherText);
        SecretKeySpec keySpec = new SecretKeySpec(keyStr.getBytes(StandardCharsets.UTF_8), "AES");
        IvParameterSpec ivSpec = new IvParameterSpec(keyStr.getBytes(StandardCharsets.UTF_8));
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
        byte[] decryptedBytes = cipher.doFinal(cipherBytes);
        return new String(decryptedBytes, StandardCharsets.UTF_8);
    }
}
