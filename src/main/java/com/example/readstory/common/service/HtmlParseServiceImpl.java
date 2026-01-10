package com.example.readstory.common.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class HtmlParseServiceImpl implements HtmlParseService {

    @Override
    public Document getDocument(String url) {
        try {
            Connection conn = Jsoup.connect(url);
            return conn.get();
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    @Nullable
    public Elements getElements(Element body, String query) {
        if (body == null || StringUtils.isBlank(query)) {
            log.error("#HtmlParseService: Document is null or query is blank");
            throw new RuntimeException("Document is null or query is blank");
        }
        return body.select(query);
    }

    @Override
    public Element getFirstElement(Element body, String query) {
        if (body == null || StringUtils.isBlank(query)) {
            log.error("#HtmlParseService: Document is null or query is blank");
            throw new RuntimeException("Document is null or query is blank");
        }
        return body.selectFirst(query);
    }

    @Override
    public Element getElementById(Element body, String idQuery) {
        if (body == null || StringUtils.isBlank(idQuery)) {
            log.error("#HtmlParseService: Document is null or query is blank");
            throw new RuntimeException("Document is null or query is blank");
        }
        return body.getElementById(idQuery);
    }
}
