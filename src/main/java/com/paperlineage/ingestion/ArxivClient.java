package com.paperlineage.ingestion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Component
public class ArxivClient {

    private static final Logger log = LoggerFactory.getLogger(ArxivClient.class);
    private static final String API_URL = "https://export.arxiv.org/api/query?id_list=";
    private static final String PDF_BASE = "https://arxiv.org/pdf/";

    private final WebClient webClient;

    public ArxivClient(WebClient.Builder builder) {
        this.webClient = builder.build();
    }

    public PaperMetadata fetch(String arxivId) {
        log.info("Fetching arXiv metadata for {}", arxivId);
        String xml = webClient.get()
                .uri(API_URL + arxivId)
                .retrieve()
                .bodyToMono(String.class)
                .block();
        return parseAtom(arxivId, xml);
    }

    private PaperMetadata parseAtom(String arxivId, String xml) {
        try {
            Document doc = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder()
                    .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
            doc.getDocumentElement().normalize();

            // index 0 is the feed <title>, index 1 is the entry <title>
            String title = text(doc, "title", 1);
            String summary = text(doc, "summary", 0).strip();
            String published = text(doc, "published", 0);
            int year = Integer.parseInt(published.substring(0, 4));

            NodeList nameNodes = doc.getElementsByTagName("name");
            List<String> authors = new ArrayList<>();
            for (int i = 0; i < nameNodes.getLength(); i++) {
                authors.add(nameNodes.item(i).getTextContent().strip());
            }

            return new PaperMetadata(arxivId, title, authors, year, summary, "", PDF_BASE + arxivId);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse arXiv response for " + arxivId, e);
        }
    }

    private String text(Document doc, String tag, int index) {
        NodeList nodes = doc.getElementsByTagName(tag);
        return (nodes.getLength() > index) ? nodes.item(index).getTextContent().strip() : "";
    }
}
