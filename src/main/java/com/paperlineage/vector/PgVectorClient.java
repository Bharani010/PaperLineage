package com.paperlineage.vector;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class PgVectorClient {

    private static final Logger log = LoggerFactory.getLogger(PgVectorClient.class);

    private final WebClient webClient;
    private final String serviceKey;

    public PgVectorClient(WebClient.Builder webClientBuilder,
                          @Value("${SUPABASE_URL:}") String supabaseUrl,
                          @Value("${SUPABASE_SERVICE_KEY:}") String serviceKey) {
        this.webClient = webClientBuilder.baseUrl(supabaseUrl).build();
        this.serviceKey = serviceKey;
    }

    public void store(String source, String chunkText, float[] embedding) {
        log.info("Storing embedding for source={}", source);
        webClient.post()
                .uri("/rest/v1/embeddings")
                .headers(this::defaultHeaders)
                .header("Prefer", "return=minimal")
                .bodyValue(Map.of(
                        "source", source,
                        "chunk_text", chunkText,
                        "embedding", toVectorString(embedding)
                ))
                .retrieve()
                .toBodilessEntity()
                .block();
    }

    public List<EmbeddingChunk> findSimilar(float[] queryEmbedding, int topK) {
        log.info("Querying top-{} similar chunks", topK);
        try {
            JsonNode response = webClient.post()
                    .uri("/rest/v1/rpc/match_embeddings")
                    .headers(this::defaultHeaders)
                    .bodyValue(Map.of(
                            "query_embedding", toVectorString(queryEmbedding),
                            "match_count", topK
                    ))
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();
            log.info("match_embeddings response: {}", response);
            return parseChunks(response);
        } catch (Exception e) {
            log.warn("match_embeddings RPC failed: {}", e.getMessage());
            return List.of();
        }
    }

    public void deleteBySource(String source) {
        webClient.delete()
                .uri(u -> u.path("/rest/v1/embeddings").queryParam("source", "eq." + source).build())
                .headers(this::defaultHeaders)
                .retrieve()
                .toBodilessEntity()
                .block();
    }

    private List<EmbeddingChunk> parseChunks(JsonNode response) {
        List<EmbeddingChunk> results = new ArrayList<>();
        if (response == null || !response.isArray()) return results;
        for (JsonNode row : response) {
            results.add(new EmbeddingChunk(
                    row.hasNonNull("id") ? row.path("id").asLong() : null,
                    row.path("source").asText(null),
                    row.path("chunk_text").asText(null),
                    null
            ));
        }
        return results;
    }

    private void defaultHeaders(org.springframework.http.HttpHeaders h) {
        h.set("apikey", serviceKey);
        h.set("Authorization", "Bearer " + serviceKey);
        h.set("Content-Type", "application/json");
    }

    private String toVectorString(float[] embedding) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(String.format("%.8f", embedding[i]));
        }
        sb.append("]");
        return sb.toString();
    }
}
