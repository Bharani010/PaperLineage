package com.paperlineage.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paperlineage.chat.ChatService;
import com.paperlineage.chat.HybridQueryEngine;
import com.paperlineage.chat.QueryResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;

@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(ChatWebSocketHandler.class);

    private final HybridQueryEngine hybridQueryEngine;
    private final ChatService chatService;
    private final ObjectMapper objectMapper;

    public ChatWebSocketHandler(HybridQueryEngine hybridQueryEngine,
                                ChatService chatService,
                                ObjectMapper objectMapper) {
        this.hybridQueryEngine = hybridQueryEngine;
        this.chatService = chatService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        JsonNode json = objectMapper.readTree(payload);
        String userMessage = json.path("message").asText("").trim();

        if (userMessage.isBlank()) {
            session.sendMessage(new TextMessage("{\"error\":\"message field is required\"}"));
            return;
        }

        log.info("Chat WS session={} message='{}'", session.getId(), userMessage);

        try {
            QueryResult queryResult = hybridQueryEngine.query(userMessage);

            chatService.streamResponse(queryResult.context(), userMessage)
                    .onErrorResume(e -> {
                        log.error("Stream error: {}", e.getMessage());
                        return reactor.core.publisher.Flux.just("[Error: " + e.getMessage() + "]");
                    })
                    .toIterable()
                    .forEach(chunk -> {
                        try {
                            if (session.isOpen()) {
                                session.sendMessage(new TextMessage(
                                        objectMapper.writeValueAsString(Map.of("chunk", chunk))));
                            }
                        } catch (Exception e) {
                            log.error("WS send error: {}", e.getMessage());
                        }
                    });

            if (session.isOpen()) {
                session.sendMessage(new TextMessage(
                        objectMapper.writeValueAsString(Map.of(
                                "done", true,
                                "sources", queryResult.sources()))));
            }
        } catch (Exception e) {
            log.error("Chat error session={}: {}", session.getId(), e.getMessage());
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(
                        objectMapper.writeValueAsString(Map.of("error", e.getMessage()))));
            }
        }
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("WS connected: {}", session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        log.info("WS closed: {} status={}", session.getId(), status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("WS transport error session={}: {}", session.getId(), exception.getMessage());
    }
}
