package com.paperlineage.ingestion;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;

@Service
public class RunGuideService {

    private static final Logger log = LoggerFactory.getLogger(RunGuideService.class);
    private static final String GROQ_URL = "https://api.groq.com/openai/v1/chat/completions";
    private static final String MODEL = "llama-3.3-70b-versatile";

    record Msg(String role, String content) {}
    record RespFmt(String type) {}
    record Req(
            String model,
            List<Msg> messages,
            @JsonProperty("max_tokens") int maxTokens,
            @JsonProperty("response_format") RespFmt responseFormat
    ) {}

    private final WebClient webClient;
    private final ObjectMapper mapper;
    private final String apiKey;

    public RunGuideService(WebClient.Builder builder,
                           ObjectMapper mapper,
                           @Value("${groq.api-key}") String apiKey) {
        this.webClient = builder.build();
        this.mapper    = mapper;
        this.apiKey    = apiKey;
    }

    @Cacheable(value = "runGuide", key = "#repoName")
    public RunGuide generate(String repoName, String repoUrl,
                             boolean hasDocker, boolean hasCi, boolean hasDeps,
                             int score, String paperTitle) {
        log.info("Generating run guide for repo={}", repoName);

        String userPrompt = buildPrompt(repoName, repoUrl, hasDocker, hasCi, hasDeps, score, paperTitle);

        Req body = new Req(
                MODEL,
                List.of(
                        new Msg("system",
                                "You are a developer-experience expert. "
                                + "Return ONLY valid JSON matching the requested schema. No markdown, no prose."),
                        new Msg("user", userPrompt)
                ),
                800,
                new RespFmt("json_object")
        );

        JsonNode response = webClient.post()
                .uri(GROQ_URL)
                .header("Authorization", "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .onStatus(s -> s.is4xxClientError() || s.is5xxServerError(),
                        r -> r.bodyToMono(String.class).map(err -> {
                            log.error("Groq error {}: {}", r.statusCode(), err);
                            return new RuntimeException(r.statusCode() + ": " + err);
                        }))
                .bodyToMono(JsonNode.class)
                .block(Duration.ofSeconds(30));

        try {
            String content = response.path("choices").path(0).path("message").path("content").asText("");
            return mapper.readValue(content, RunGuide.class);
        } catch (Exception e) {
            log.error("Failed to parse run guide JSON for {}: {}", repoName, e.getMessage());
            throw new RuntimeException("Guide parse failed: " + e.getMessage());
        }
    }

    private String buildPrompt(String repoName, String repoUrl,
                                boolean hasDocker, boolean hasCi, boolean hasDeps,
                                int score, String paperTitle) {
        return """
                Generate a practical "How to Run" guide for this ML paper implementation.

                Repository: %s
                URL: %s
                Paper: %s
                Runnability score: %d/100
                Has Docker: %s
                Has CI/CD: %s
                Has dependency files (requirements.txt / pyproject.toml / etc.): %s

                Return ONLY this JSON structure:
                {
                  "difficulty": "Easy" | "Medium" | "Hard",
                  "estimatedTime": "<e.g. 10-20 min>",
                  "prerequisites": ["<software/hardware requirement>"],
                  "steps": [
                    { "title": "<step name>", "command": "<shell command or empty string>", "notes": "<optional tip or empty string>" }
                  ],
                  "commonIssues": ["<concise issue description>"]
                }

                Be specific and practical. Prefer Docker steps if available. Keep steps to 5-8 max.
                """.formatted(repoName, repoUrl, paperTitle, score,
                        hasDocker, hasCi, hasDeps);
    }
}
