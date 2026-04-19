package com.paperlineage.ingestion;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Searches the HuggingFace Hub for model cards linked to a paper.
 *
 * Strategy (in order):
 *   1. Filter by arxiv tag  → models whose tags include "arxiv:{id}"
 *   2. Search by arXiv ID   → catches models that embed the ID in their card text
 *   3. Search by title keywords (first 4 significant words) — fallback
 *
 * No API key required for public models.
 */
@Service
public class HuggingFaceClient {

    private static final Logger log = LoggerFactory.getLogger(HuggingFaceClient.class);
    private static final String BASE = "https://huggingface.co";
    private static final Duration TIMEOUT = Duration.ofSeconds(8);
    private static final int LIMIT = 5;

    private final WebClient webClient;

    public HuggingFaceClient(WebClient.Builder builder) {
        this.webClient = builder.baseUrl(BASE)
                .defaultHeader("Accept", "application/json")
                .build();
    }

    public List<HfModelInfo> search(String arxivId, String paperTitle) {
        log.info("Searching HuggingFace for arxivId={}", arxivId);

        // Strategy 1: exact arxiv tag filter
        List<HfModelInfo> results = queryModels(
                "/api/models?filter=arxiv:" + arxivId + "&sort=downloads&direction=-1&limit=" + LIMIT
        );

        // Strategy 2: text search by arxiv ID
        if (results.isEmpty()) {
            results = queryModels(
                    "/api/models?search=" + arxivId + "&sort=downloads&direction=-1&limit=" + LIMIT
            );
        }

        // Strategy 3: title keyword search
        if (results.isEmpty() && paperTitle != null && !paperTitle.isBlank()) {
            String keywords = titleKeywords(paperTitle);
            results = queryModels(
                    "/api/models?search=" + encode(keywords) + "&sort=downloads&direction=-1&limit=" + LIMIT
            );
        }

        log.info("HuggingFace found {} models for arxivId={}", results.size(), arxivId);
        return results;
    }

    private List<HfModelInfo> queryModels(String path) {
        try {
            JsonNode node = webClient.get()
                    .uri(path)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .timeout(TIMEOUT)
                    .onErrorResume(e -> {
                        log.warn("HF call failed path={}: {}", path, e.getMessage());
                        return Mono.empty();
                    })
                    .block();

            if (node == null || !node.isArray()) return List.of();

            List<HfModelInfo> models = new ArrayList<>();
            for (JsonNode m : node) {
                String modelId = m.path("id").asText(m.path("modelId").asText(""));
                if (modelId.isBlank()) continue;

                models.add(new HfModelInfo(
                        modelId,
                        m.path("pipeline_tag").asText(""),
                        m.path("downloads").asInt(0),
                        m.path("likes").asInt(0),
                        m.path("lastModified").asText("")
                ));
            }
            return models;
        } catch (Exception e) {
            log.warn("HF query exception: {}", e.getMessage());
            return List.of();
        }
    }

    /** First 4 meaningful words from title, lowercased, joined by '+' for URL query. */
    private String titleKeywords(String title) {
        String[] stopWords = {"a", "an", "the", "of", "for", "in", "on", "with", "and", "or", "is", "are", "to"};
        java.util.Set<String> stop = new java.util.HashSet<>(Arrays.asList(stopWords));

        return Arrays.stream(title.replaceAll("[^a-zA-Z0-9 ]", " ").trim().split("\\s+"))
                .filter(w -> w.length() > 2 && !stop.contains(w.toLowerCase()))
                .limit(4)
                .collect(Collectors.joining(" "));
    }

    private String encode(String s) {
        return s.replace(" ", "+");
    }
}
