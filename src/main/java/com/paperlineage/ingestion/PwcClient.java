package com.paperlineage.ingestion;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Client for the Papers With Code public API (no auth required).
 * Makes at most 2 sequential calls: one to find the paper, one for benchmark results.
 */
@Service
public class PwcClient {

    private static final Logger log = LoggerFactory.getLogger(PwcClient.class);
    private static final String BASE = "https://paperswithcode.com/api/v1";
    private static final Duration TIMEOUT = Duration.ofSeconds(8);
    private static final int MAX_RESULTS = 5;

    private final WebClient webClient;

    public PwcClient(WebClient.Builder builder) {
        this.webClient = builder.baseUrl(BASE)
                .defaultHeader("Accept", "application/json")
                .build();
    }

    public PwcData fetch(String arxivId) {
        log.info("Fetching PWC data for arxivId={}", arxivId);

        // 1. Look up paper by arXiv ID
        JsonNode paperNode = get("/papers/?arxiv_id=" + arxivId);
        if (paperNode == null || paperNode.path("count").asInt(0) == 0) {
            log.info("Paper {} not found on Papers With Code", arxivId);
            return PwcData.notFound();
        }

        JsonNode paper = paperNode.path("results").get(0);
        String pwcId = paper.path("id").asText(null);
        if (pwcId == null) return PwcData.notFound();

        List<String> tasks   = toStringList(paper.path("tasks"));
        List<String> methods = extractMethodNames(paper.path("methods"));

        // 2. Fetch benchmark results for this paper
        List<PwcData.BenchmarkResult> benchmarks = fetchBenchmarks(pwcId);

        log.info("PWC: {} tasks={} methods={} benchmarks={}", arxivId, tasks.size(), methods.size(), benchmarks.size());
        return new PwcData(true, pwcId, tasks, methods, benchmarks);
    }

    private List<PwcData.BenchmarkResult> fetchBenchmarks(String pwcId) {
        JsonNode node = get("/papers/" + pwcId + "/results/?items_per_page=" + MAX_RESULTS);
        if (node == null || !node.has("results")) return List.of();

        List<PwcData.BenchmarkResult> out = new ArrayList<>();
        for (JsonNode result : node.path("results")) {
            String task    = result.path("task").asText("");
            String dataset = result.path("dataset").asText("");

            // Each result can have multiple metrics — take the first SOTA one, else first
            JsonNode metrics = result.path("metrics");
            if (metrics.isArray() && metrics.size() > 0) {
                JsonNode best = pickBestMetric(metrics);
                if (best != null) {
                    out.add(new PwcData.BenchmarkResult(
                            task, dataset,
                            best.path("type").asText(""),
                            best.path("value").asText(""),
                            best.path("is_sota").asBoolean(false)
                    ));
                }
            }
            if (out.size() >= MAX_RESULTS) break;
        }
        return out;
    }

    private JsonNode pickBestMetric(JsonNode metrics) {
        JsonNode sota = null;
        for (JsonNode m : metrics) {
            if (m.path("is_sota").asBoolean(false)) {
                sota = m;
                break;
            }
        }
        return sota != null ? sota : metrics.get(0);
    }

    private JsonNode get(String path) {
        try {
            return webClient.get()
                    .uri(path)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .timeout(TIMEOUT)
                    .onErrorResume(e -> {
                        log.warn("PWC call failed path={}: {}", path, e.getMessage());
                        return Mono.empty();
                    })
                    .block();
        } catch (Exception e) {
            log.warn("PWC call exception path={}: {}", path, e.getMessage());
            return null;
        }
    }

    private List<String> toStringList(JsonNode arrayNode) {
        List<String> list = new ArrayList<>();
        if (arrayNode.isArray()) {
            for (JsonNode item : arrayNode) {
                String val = item.isTextual() ? item.asText() : item.path("name").asText("");
                if (!val.isBlank()) list.add(val);
            }
        }
        return list;
    }

    private List<String> extractMethodNames(JsonNode methodsNode) {
        List<String> list = new ArrayList<>();
        if (methodsNode.isArray()) {
            for (JsonNode m : methodsNode) {
                String name = m.path("name").asText("");
                if (!name.isBlank()) list.add(name);
            }
        }
        return list;
    }
}
