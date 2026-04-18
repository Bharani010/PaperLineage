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
    private static final String MODEL_URL =
            "https://api-inference.huggingface.co/models/sentence-transformers/all-MiniLM-L6-v2";
    private static final int DIMENSIONS = 384;

    private final WebClient webClient;
    private final String apiKey;

    public EmbeddingClient(WebClient.Builder webClientBuilder,
                           @Value("${HUGGINGFACE_API_KEY:}") String apiKey) {
        this.webClient = webClientBuilder.baseUrl(MODEL_URL).build();
        this.apiKey = apiKey;
    }

    public float[] embed(String text) {
        List<float[]> batch = embedBatch(List.of(text));
        return batch.isEmpty() ? new float[DIMENSIONS] : batch.get(0);
    }

    public List<float[]> embedBatch(List<String> texts) {
        log.info("Embedding {} text chunk(s) via HuggingFace", texts.size());
        try {
            JsonNode response = webClient.post()
                    .uri("")
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .bodyValue(Map.of("inputs", texts))
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .retryWhen(Retry.fixedDelay(3, Duration.ofSeconds(5))
                            .filter(this::isRetryable))
                    .block();
            return parseEmbeddings(response);
        } catch (WebClientResponseException e) {
            log.warn("HuggingFace embedding failed ({} {}): {}",
                    e.getStatusCode().value(), e.getStatusText(), e.getResponseBodyAsString());
            return List.of();
        } catch (Exception e) {
            log.warn("HuggingFace embedding failed: {}", e.getMessage());
            return List.of();
        }
    }

    private List<float[]> parseEmbeddings(JsonNode response) {
        List<float[]> result = new ArrayList<>();
        if (response == null || !response.isArray()) return result;

        for (JsonNode row : response) {
            // row is either a float array [0.1, 0.2, ...] directly
            // or a nested array [[0.1, 0.2, ...]] (some models wrap it)
            JsonNode vectorNode = row.isArray() && row.get(0) != null && row.get(0).isArray()
                    ? row.get(0) : row;
            float[] vec = new float[vectorNode.size()];
            for (int i = 0; i < vectorNode.size(); i++) {
                vec[i] = (float) vectorNode.get(i).asDouble();
            }
            result.add(vec);
        }
        return result;
    }

    private boolean isRetryable(Throwable e) {
        if (e instanceof WebClientResponseException ex) {
            return ex.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS
                    || ex.getStatusCode() == HttpStatus.SERVICE_UNAVAILABLE
                    || ex.getStatusCode() == HttpStatus.BAD_GATEWAY;
        }
        return false;
    }
}
