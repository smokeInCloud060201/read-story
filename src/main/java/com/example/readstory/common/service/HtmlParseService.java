package com.example.readstory.common.service;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public interface HtmlParseService {

    Document getDocument(String url);

    Elements getElements(Element body, String query);

    Element getFirstElement(Element body, String query);

    Element getElementById(Element body, String idQuery);
}
