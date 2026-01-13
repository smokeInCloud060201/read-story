package com.example.readstory.crawl.service.impl;

import com.example.readstory.common.annotation.Source;
import com.example.readstory.common.annotation.Strategy;
import com.example.readstory.common.service.HtmlParseService;
import com.example.readstory.crawl.dto.MTCChaptersResponse;
import com.example.readstory.crawl.service.CrawlStrategy;
import com.example.readstory.story.dto.ChapterTask;
import com.example.readstory.story.entity.Chapter;
import com.example.readstory.story.entity.Story;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.web.client.RestClient;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Strategy(name = "MTCStrategy", description = "The strategy use to crawl story in https://metruyencv.com", source = Source.MTC, baseHost = "metruyencv.com")
@Slf4j
public class MTCCrawlStrategy implements CrawlStrategy {

    private static final String BASE_URL = "https://metruyencv.com";
    private static final String BACKEND_BASE_URL = "https://backend.metruyencv.com";
    private volatile String cachedKey = null;

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
        final Element bookDataEle = htmlParseService.getFirstElement(document, "script:containsData(window.bookData)");
        final String bookData = bookDataEle.data();

        // Extract book_id from window.bookData = { "book": { "id": 136693, ... } }
        Pattern pattern = Pattern.compile("\"id\":\\s*(\\d+)");
        Matcher matcher = pattern.matcher(bookData);
        if (!matcher.find()) {
            log.error("Could not find book_id in page: {}", url);
            return List.of();
        }
        String bookId = matcher.group(1);
        log.info("#MTCCrawlStrategy: Found book_id {} for {}", bookId, story.getName());

        String apiUrl = String.format("/api/chapters?filter[book_id]=%s&filter[type]=published", bookId);

        try {
            RestClient restClient = RestClient.builder().baseUrl(BACKEND_BASE_URL).build();

            MTCChaptersResponse chaptersResponse = restClient.get().uri(apiUrl).retrieve().body(MTCChaptersResponse.class);

            if (chaptersResponse == null || chaptersResponse.getData() == null) {
                log.error("Failed to fetch chapters from API: {} response: {}", apiUrl, chaptersResponse);
                return List.of();
            }

            return chaptersResponse.getData().stream().map(s -> new ChapterTask(s.getName(), Integer.parseInt(s.getIndex()), s.getIndex())).collect(Collectors.toList());
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
            Document document = htmlParseService.getDocument(chapterUrl);
            final Element bookDataEle = htmlParseService.getFirstElement(document, "script:containsData(window.chapterData)");
            final String bookData = bookDataEle.data();

            // Extract content from window.chapterData = { chapter: { ..., content: "..." }
            // }
            Pattern pattern = Pattern.compile("\\bcontent\\s*:\\s*\"([^\"]+)\"");
            Matcher matcher = pattern.matcher(bookData);

            if (!matcher.find()) {
                log.error("Could not find encoded content in chapter page: {}", chapterUrl);
                return null;
            }

            if (cachedKey == null) {
                refreshKey(document);
            }

            String encodedContent = matcher.group(1).replaceAll("\\\\/", "/");
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

    private String decrypt(String cipherText) {
        if (cipherText == null || cipherText.isEmpty()) return "";

        try {
            String keyStr = cachedKey != null ? cachedKey : "cNdR17YqKmWx9BgT"; // Fallback to last known good key
            byte[] cipherBytes = Base64.getDecoder().decode(cipherText);
            SecretKeySpec keySpec = new SecretKeySpec(keyStr.getBytes(StandardCharsets.UTF_8), "AES");
            IvParameterSpec ivSpec = new IvParameterSpec(keyStr.getBytes(StandardCharsets.UTF_8));
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
            byte[] decryptedBytes = cipher.doFinal(cipherBytes);
            return new String(decryptedBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("#MTCCrawlStrategy: AES Decryption failed", e);
            return "";
        }
    }

    private void refreshKey(Document document) {
        try {
            // Find script like <script type="module"
            // src="https://assets.metruyencv.com/build/assets/app-9327baa8.js"></script>
            Element scriptTag = document.select("script[src*=/build/assets/app-]").first();
            if (scriptTag == null) {
                log.warn("#MTCCrawlStrategy: Could not find main application JS tag");
                return;
            }

            String jsUrl = scriptTag.attr("src");
            if (jsUrl.startsWith("/")) {
                jsUrl = BASE_URL + jsUrl;
            }

            log.info("#MTCCrawlStrategy: Fetching JS bundle for key extraction: {}", jsUrl);
            RestClient restClient = RestClient.create();
            String jsContent = restClient.get().uri(jsUrl).retrieve().body(String.class);

            if (jsContent == null || jsContent.isEmpty()) {
                log.warn("#MTCCrawlStrategy: JS bundle is empty");
                return;
            }

            // Look for [84,103,66,57,120,87,109,75,113,89,55,49,82,100,78,99]
            Pattern pattern = Pattern.compile("\\[(\\d+(?:,\\s*\\d+){15})\\]");
            Matcher matcher = pattern.matcher(jsContent);

            if (matcher.find()) {
                String codesStr = matcher.group(1);
                String[] codes = codesStr.split(",\\s*");
                StringBuilder sb = new StringBuilder();
                for (String code : codes) {
                    sb.append((char) Integer.parseInt(code));
                }
                // Reverse the string
                this.cachedKey = sb.reverse().toString();
                log.info("#MTCCrawlStrategy: Successfully extracted and cached AES key: {}", this.cachedKey);
            } else {
                log.warn("#MTCCrawlStrategy: Could not find keyCodes array in JS bundle");
            }
        } catch (Exception e) {
            log.error("#MTCCrawlStrategy: Failed to refresh AES key", e);
        }
    }
}
