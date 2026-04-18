package com.paperlineage.ingestion;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Service
public class CitationFetcher {

    private static final Logger log = LoggerFactory.getLogger(CitationFetcher.class);

    private static final String BASE_URL = "https://api.semanticscholar.org/graph/v1";
    private static final String FIELDS =
            "paperId,title,authors,year,citationCount,references,citations";

    private final WebClient webClient;
    private final String apiKey;

    public CitationFetcher(WebClient.Builder webClientBuilder,
                           @Value("${SEMANTIC_SCHOLAR_API_KEY:}") String apiKey) {
        this.webClient = webClientBuilder.baseUrl(BASE_URL).build();
        this.apiKey = apiKey;
    }

    public CitationGraph fetch(String arxivId) {
        log.info("Fetching citations for arXiv:{}", arxivId);

        JsonNode root = webClient.get()
                .uri("/paper/arXiv:{id}?fields={fields}", arxivId, FIELDS)
                .headers(h -> {
                    if (apiKey != null && !apiKey.isBlank()) {
                        h.set("x-api-key", apiKey);
                    }
                })
                .retrieve()
                .bodyToMono(JsonNode.class)
                .retryWhen(Retry.fixedDelay(1, Duration.ofSeconds(2))
                        .filter(e -> e instanceof WebClientResponseException.TooManyRequests))
                .block();

        if (root == null) {
            return new CitationGraph(null, null, List.of(), List.of(), 0, 0);
        }

        String paperId = textOrNull(root, "paperId");
        String title = textOrNull(root, "title");

        List<CitationEntry> forward = parseEntries(root.path("citations"));
        List<CitationEntry> backward = parseEntries(root.path("references"));

        return new CitationGraph(paperId, title, forward, backward,
                forward.size(), backward.size());
    }

    private List<CitationEntry> parseEntries(JsonNode arrayNode) {
        List<CitationEntry> entries = new ArrayList<>();
        if (arrayNode == null || !arrayNode.isArray()) return entries;

        for (JsonNode node : arrayNode) {
            String id = textOrNull(node, "paperId");
            String t = textOrNull(node, "title");
            List<String> authors = new ArrayList<>();
            JsonNode authorsNode = node.path("authors");
            if (authorsNode.isArray()) {
                for (JsonNode a : authorsNode) {
                    String name = textOrNull(a, "name");
                    if (name != null) authors.add(name);
                }
            }
            Integer year = node.path("year").isInt() ? node.path("year").asInt() : null;
            Integer citationCount = node.path("citationCount").isInt()
                    ? node.path("citationCount").asInt() : null;
            entries.add(new CitationEntry(id, t, authors, year, citationCount));
        }
        return entries;
    }

    private String textOrNull(JsonNode node, String field) {
        JsonNode f = node.path(field);
        return f.isMissingNode() || f.isNull() ? null : f.asText();
    }
}
