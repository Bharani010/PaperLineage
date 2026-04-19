package com.paperlineage.chat;

import com.fasterxml.jackson.annotation.JsonProperty;
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
import reactor.core.publisher.Mono;

import java.util.List;

@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);
    private static final String GROQ_URL = "https://api.groq.com/openai/v1/chat/completions";
    private static final String MODEL = "llama-3.3-70b-versatile";
    private static final String SYSTEM_PROMPT =
            "You are a research assistant specialising in academic papers and their implementations. " +
            "Answer questions concisely and accurately based on the provided context. " +
            "Focus on research contributions, citation relationships, and implementation details.";

    // ── Request records (OpenAI-compatible format) ───────────

    record ChatMessage(String role, String content) {}

    record ChatRequest(
            String model,
            List<ChatMessage> messages,
            @JsonProperty("max_tokens") int maxTokens,
            boolean stream
    ) {}

    // ─────────────────────────────────────────────────────────

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;

    public ChatService(WebClient.Builder webClientBuilder,
                       ObjectMapper objectMapper,
                       @Value("${groq.api-key}") String apiKey) {
        this.webClient = webClientBuilder.build();
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
    }

    public Flux<String> streamResponse(String context, String userMessage) {
        String userContent = context.isBlank()
                ? userMessage
                : "Context:\n" + context + "\n\nQuestion: " + userMessage;

        ChatRequest body = new ChatRequest(
                MODEL,
                List.of(
                        new ChatMessage("system", SYSTEM_PROMPT),
                        new ChatMessage("user", userContent)
                ),
                1024,
                true
        );

        log.info("Groq request: model={} contextLen={}", MODEL, context.length());

        return webClient.post()
                .uri(GROQ_URL)
                .header("Authorization", "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue(body)
                .retrieve()
                .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                        response -> response.bodyToMono(String.class).flatMap(errorBody -> {
                            log.error("Groq API error {}: {}", response.statusCode(), errorBody);
                            return Mono.error(new RuntimeException(
                                    response.statusCode() + " from Groq: " + errorBody));
                        }))
                .bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<String>>() {})
                .mapNotNull(event -> extractDelta(event.data()))
                .filter(text -> !text.isEmpty())
                .doOnError(e -> log.error("Groq stream error: {}", e.getMessage()));
    }

    private String extractDelta(String data) {
        if (data == null || data.isBlank() || "[DONE]".equals(data.trim())) return null;
        try {
            JsonNode node = objectMapper.readTree(data);
            JsonNode content = node.path("choices").path(0).path("delta").path("content");
            if (!content.isMissingNode() && !content.isNull()) {
                return content.asText("");
            }
        } catch (Exception e) {
            log.debug("SSE parse skip: {}", data);
        }
        return null;
    }
}
