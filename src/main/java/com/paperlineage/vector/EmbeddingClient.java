package com.paperlineage.vector;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class EmbeddingClient {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingClient.class);
    private static final String BASE_URL = "https://api.cohere.com";
    private static final String MODEL = "embed-english-light-v3.0";
    private static final int DIMENSIONS = 384;

    private final WebClient webClient;
    private final String apiKey;

    public EmbeddingClient(WebClient.Builder webClientBuilder,
                           @Value("${COHERE_API_KEY:}") String apiKey) {
        this.webClient = webClientBuilder.baseUrl(BASE_URL).build();
        this.apiKey = apiKey;
        log.info("EmbeddingClient init: provider=Cohere, apiKey={}", apiKey.isBlank() ? "MISSING" : "set (" + apiKey.length() + " chars)");
    }

    public float[] embed(String text) {
        List<float[]> batch = embedBatch(List.of(text), "search_document");
        return batch.isEmpty() ? new float[0] : batch.get(0);
    }

    public float[] embedQuery(String text) {
        List<float[]> batch = embedBatch(List.of(text), "search_query");
        return batch.isEmpty() ? new float[0] : batch.get(0);
    }

    public List<float[]> embedBatch(List<String> texts, String inputType) {
        log.info("Embedding {} text(s) via Cohere ({})", texts.size(), inputType);
        try {
            JsonNode response = webClient.post()
                    .uri("/v2/embed")
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .bodyValue(Map.of(
                            "model", MODEL,
                            "texts", texts,
                            "input_type", inputType,
                            "embedding_types", List.of("float")
                    ))
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .retryWhen(Retry.fixedDelay(2, Duration.ofSeconds(3))
                            .filter(this::isRetryable))
                    .block();
            return parseEmbeddings(response);
        } catch (WebClientResponseException e) {
            log.warn("Cohere embedding failed ({} {}): {}", e.getStatusCode().value(), e.getStatusText(), e.getResponseBodyAsString());
            return List.of();
        } catch (Exception e) {
            log.warn("Cohere embedding failed: {}", e.getMessage());
            return List.of();
        }
    }

    private List<float[]> parseEmbeddings(JsonNode response) {
        List<float[]> result = new ArrayList<>();
        if (response == null) return result;
        JsonNode floatArrays = response.path("embeddings").path("float");
        if (!floatArrays.isArray()) return result;
        for (JsonNode row : floatArrays) {
            float[] vec = new float[row.size()];
            for (int i = 0; i < row.size(); i++) {
                vec[i] = (float) row.get(i).asDouble();
            }
            result.add(vec);
        }
        return result;
    }

    private boolean isRetryable(Throwable e) {
        if (e instanceof WebClientResponseException ex) {
            return ex.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS
                    || ex.getStatusCode() == HttpStatus.SERVICE_UNAVAILABLE;
        }
        return false;
    }
}
