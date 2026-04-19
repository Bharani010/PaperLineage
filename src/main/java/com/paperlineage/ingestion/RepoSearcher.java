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
import java.util.Arrays;
import java.util.List;

@Service
public class RepoSearcher {

    private static final Logger log = LoggerFactory.getLogger(RepoSearcher.class);
    private static final String BASE_URL = "https://api.github.com";
    private static final int MAX_RESULTS = 10;

    private final WebClient webClient;
    private final String githubToken;

    public RepoSearcher(WebClient.Builder webClientBuilder,
                        @Value("${GITHUB_TOKEN:}") String githubToken) {
        this.webClient = webClientBuilder.baseUrl(BASE_URL).build();
        this.githubToken = githubToken;
    }

    public List<RepoResult> search(PaperMetadata paper) {
        String query = buildQuery(paper);
        log.info("Searching GitHub repos for arXiv:{} query={}", paper.arxivId(), query);

        JsonNode root = webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/search/repositories")
                        .queryParam("q", query)
                        .queryParam("sort", "stars")
                        .queryParam("order", "desc")
                        .queryParam("per_page", MAX_RESULTS)
                        .build())
                .headers(h -> {
                    h.set("Accept", "application/vnd.github+json");
                    h.set("X-GitHub-Api-Version", "2022-11-28");
                    if (githubToken != null && !githubToken.isBlank()) {
                        h.set("Authorization", "Bearer " + githubToken);
                    }
                })
                .retrieve()
                .bodyToMono(JsonNode.class)
                .retryWhen(Retry.fixedDelay(1, Duration.ofSeconds(3))
                        .filter(e -> e instanceof WebClientResponseException.TooManyRequests))
                .block();

        if (root == null || !root.has("items")) {
            log.warn("No items in GitHub search response for arXiv:{}", paper.arxivId());
            return List.of();
        }

        List<RepoResult> results = new ArrayList<>();
        for (JsonNode item : root.path("items")) {
            if (results.size() >= MAX_RESULTS) break;
            results.add(new RepoResult(
                    textOrNull(item, "full_name"),
                    textOrNull(item, "html_url"),
                    textOrNull(item, "description"),
                    textOrNull(item, "language"),
                    item.path("stargazers_count").asInt(0),
                    item.path("forks_count").asInt(0),
                    item.path("open_issues_count").asInt(0)
            ));
        }
        return results;
    }

    private String buildQuery(PaperMetadata paper) {
        if (paper.title() == null) return "machine learning paper implementation";
        String[] words = paper.title()
                .replaceAll("[^a-zA-Z0-9 ]", " ")
                .trim()
                .split("\\s+");
        String titleWords = String.join(" ", Arrays.copyOf(words, Math.min(6, words.length)));
        return titleWords + " implementation paper";
    }

    private String textOrNull(JsonNode node, String field) {
        JsonNode f = node.path(field);
        return f.isMissingNode() || f.isNull() ? null : f.asText();
    }
}
