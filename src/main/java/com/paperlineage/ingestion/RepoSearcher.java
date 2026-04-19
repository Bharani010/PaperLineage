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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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

    /**
     * Three-strategy search, in priority order:
     * 1. Direct GitHub URLs extracted from the paper PDF (highest signal)
     * 2. GitHub search by arXiv ID in README / description
     * 3. GitHub search by title keywords (broadest fallback)
     */
    public List<RepoResult> search(PaperMetadata paper) {
        Set<String> seen = new LinkedHashSet<>();
        List<RepoResult> results = new ArrayList<>();

        // ── Strategy 1: direct repos linked in the PDF ─────────────────
        for (String repoPath : paper.directRepoUrls()) {
            if (results.size() >= MAX_RESULTS) break;
            RepoResult r = fetchRepo(repoPath);
            if (r != null && seen.add(r.fullName())) {
                results.add(r);
                log.info("Found via PDF link: {}", r.fullName());
            }
        }

        // ── Strategy 2: search by arXiv ID in README/description ───────
        if (results.size() < MAX_RESULTS) {
            String idQuery = "\"" + paper.arxivId() + "\" in:readme,description,name";
            for (RepoResult r : searchGitHub(idQuery, MAX_RESULTS)) {
                if (results.size() >= MAX_RESULTS) break;
                if (seen.add(r.fullName())) {
                    results.add(r);
                }
            }
        }

        // ── Strategy 3: title keyword search ───────────────────────────
        if (results.size() < MAX_RESULTS) {
            String titleQuery = buildTitleQuery(paper);
            for (RepoResult r : searchGitHub(titleQuery, MAX_RESULTS)) {
                if (results.size() >= MAX_RESULTS) break;
                if (seen.add(r.fullName())) {
                    results.add(r);
                }
            }
        }

        log.info("Repo search done arXiv:{} direct={} total={}", paper.arxivId(),
                paper.directRepoUrls().size(), results.size());
        return results;
    }

    // ── GitHub API calls ──────────────────────────────────────────────

    /** Fetch a single repo by owner/repo path — used for direct PDF links. */
    private RepoResult fetchRepo(String ownerRepo) {
        try {
            JsonNode item = webClient.get()
                    .uri("/repos/" + ownerRepo)
                    .headers(this::setHeaders)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block(Duration.ofSeconds(8));

            return item == null ? null : toRepoResult(item);
        } catch (WebClientResponseException.NotFound e) {
            log.debug("Direct repo not found: {}", ownerRepo);
            return null;
        } catch (Exception e) {
            log.warn("Direct repo fetch failed {}: {}", ownerRepo, e.getMessage());
            return null;
        }
    }

    /** GitHub search API — returns up to {@code limit} results. */
    private List<RepoResult> searchGitHub(String query, int limit) {
        try {
            JsonNode root = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/search/repositories")
                            .queryParam("q", query)
                            .queryParam("sort", "stars")
                            .queryParam("order", "desc")
                            .queryParam("per_page", Math.min(limit, 10))
                            .build())
                    .headers(this::setHeaders)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .retryWhen(Retry.fixedDelay(1, Duration.ofSeconds(3))
                            .filter(e -> e instanceof WebClientResponseException.TooManyRequests))
                    .block(Duration.ofSeconds(10));

            if (root == null || !root.has("items")) return List.of();

            List<RepoResult> results = new ArrayList<>();
            for (JsonNode item : root.path("items")) {
                if (results.size() >= limit) break;
                results.add(toRepoResult(item));
            }
            return results;
        } catch (Exception e) {
            log.warn("GitHub search failed query='{}': {}", query, e.getMessage());
            return List.of();
        }
    }

    private void setHeaders(org.springframework.http.HttpHeaders h) {
        h.set("Accept", "application/vnd.github+json");
        h.set("X-GitHub-Api-Version", "2022-11-28");
        if (githubToken != null && !githubToken.isBlank()) {
            h.set("Authorization", "Bearer " + githubToken);
        }
    }

    private RepoResult toRepoResult(JsonNode item) {
        return new RepoResult(
                textOrNull(item, "full_name"),
                textOrNull(item, "html_url"),
                textOrNull(item, "description"),
                textOrNull(item, "language"),
                item.path("stargazers_count").asInt(0),
                item.path("forks_count").asInt(0),
                item.path("open_issues_count").asInt(0)
        );
    }

    private String buildTitleQuery(PaperMetadata paper) {
        if (paper.title() == null) return "machine learning paper implementation";
        String[] words = paper.title()
                .replaceAll("[^a-zA-Z0-9 ]", " ")
                .trim()
                .split("\\s+");
        // Use up to 6 words — enough to be specific, not so many that GitHub returns zero results
        String titleWords = String.join(" ", Arrays.copyOf(words, Math.min(6, words.length)));
        return titleWords + " implementation paper";
    }

    private String textOrNull(JsonNode node, String field) {
        JsonNode f = node.path(field);
        return f.isMissingNode() || f.isNull() ? null : f.asText();
    }
}
