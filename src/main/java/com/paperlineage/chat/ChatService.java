package com.paperlineage.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);
    private static final String ANTHROPIC_URL = "https://api.anthropic.com/v1/messages";
    private static final String MODEL = "claude-haiku-4-5-20251001";
    private static final String SYSTEM_PROMPT = """
            You are a research assistant for PaperLineage, a tool that traces academic paper \
            citation lineage and maps GitHub implementations. Answer questions about academic \
            papers concisely and accurately based on the provided context. Focus on research \
            contributions, citation relationships, and implementation details.""";

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;

    public ChatService(WebClient.Builder webClientBuilder,
                       ObjectMapper objectMapper,
                       @Value("${anthropic.api-key}") String apiKey) {
        this.webClient = webClientBuilder.build();
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
    }

    public Flux<String> streamResponse(String context, String userMessage) {
        String userContent = context.isBlank()
                ? userMessage
                : "Context:\n" + context + "\n\nQuestion: " + userMessage;

        Map<String, Object> body = Map.of(
                "model", MODEL,
                "max_tokens", 1024,
                "stream", true,
                "system", SYSTEM_PROMPT,
                "messages", List.of(Map.of("role", "user", "content", userContent))
        );

        log.info("Claude request: model={} contextLen={}", MODEL, context.length());

        return webClient.post()
                .uri(ANTHROPIC_URL)
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue(body)
                .retrieve()
                .bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<String>>() {})
                .mapNotNull(event -> extractTextDelta(event.data()))
                .filter(text -> !text.isEmpty())
                .doOnError(e -> log.error("Claude stream error: {}", e.getMessage()));
    }

    private String extractTextDelta(String data) {
        if (data == null || data.isBlank()) return null;
        try {
            JsonNode node = objectMapper.readTree(data);
            if ("content_block_delta".equals(node.path("type").asText())) {
                JsonNode delta = node.path("delta");
                if ("text_delta".equals(delta.path("type").asText())) {
                    return delta.path("text").asText("");
                }
            }
        } catch (Exception e) {
            log.debug("SSE parse skip: {}", data);
        }
        return null;
    }
}
