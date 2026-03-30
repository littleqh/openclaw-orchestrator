package com.openclaw.orchestrator.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openclaw.orchestrator.entity.Worker;
import com.openclaw.orchestrator.repository.WorkerRepository;
import com.openclaw.orchestrator.service.AgentRuntimeService;
import com.openclaw.orchestrator.service.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
@Slf4j
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private final AgentRuntimeService agentRuntimeService;
    private final WorkerRepository workerRepository;
    private final JwtService jwtService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // sessionId -> workerId
    private final Map<String, Long> sessionWorkerMap = new ConcurrentHashMap<>();
    // sessionId -> sessionId for cleanup
    private final Map<String, Long> sessionIdMap = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String path = session.getUri().getPath();
        String workerIdStr = extractWorkerId(path);

        if (workerIdStr == null) {
            log.warn("[ChatWS] No workerId in path: {}", path);
            session.close(CloseStatus.BAD_DATA);
            return;
        }

        Long workerId = Long.parseLong(workerIdStr);

        // Validate JWT token from query param
        String token = session.getUri().getQuery();
        if (token != null && token.startsWith("token=")) {
            token = token.substring(6);
        }

        if (token == null || !jwtService.validateToken(token)) {
            log.warn("[ChatWS] Invalid or missing token for worker {}", workerId);
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }

        // Validate worker is local
        Worker worker = workerRepository.findById(workerId).orElse(null);
        if (worker == null) {
            log.warn("[ChatWS] Worker {} not found", workerId);
            session.close(CloseStatus.BAD_DATA);
            return;
        }

        if (!Boolean.TRUE.equals(worker.getLocalRuntime())) {
            log.warn("[ChatWS] Worker {} is not a local agent", workerId);
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }

        sessionWorkerMap.put(session.getId(), workerId);
        log.info("[ChatWS] Connection established for worker {}, session {}", workerId, session.getId());

        // Send welcome message
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(
            Map.of("type", "system", "content", "Connected to " + worker.getName())
        )));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        Long workerId = sessionWorkerMap.get(session.getId());
        if (workerId == null) {
            log.warn("[ChatWS] No workerId for session {}", session.getId());
            return;
        }

        String payload = message.getPayload();
        log.info("[ChatWS] Received message from session {}: {}", session.getId(), payload);

        try {
            Map<String, Object> data = objectMapper.readValue(payload, Map.class);
            String type = (String) data.get("type");

            if ("message".equals(type)) {
                String content = (String) data.get("content");
                Long sessionIdObj = data.get("sessionId") != null ? ((Number) data.get("sessionId")).longValue() : null;

                log.info("[ChatWS] message type: content={}, sessionId={}", content, sessionIdObj);

                if (content == null || content.trim().isEmpty()) {
                    session.sendMessage(new TextMessage(objectMapper.writeValueAsString(
                        Map.of("type", "error", "content", "Message cannot be empty")
                    )));
                    return;
                }

                // Handle chat via AgentRuntimeService
                log.info("[ChatWS] Calling agentRuntimeService.chat with workerId={}, sessionId={}", workerId, sessionIdObj);
                Flux<String> stream = agentRuntimeService.chat(workerId, sessionIdObj, content);

                // Subscribe to the stream and send chunks
                stream.subscribe(
                    chunk -> {
                        try {
                            log.info("[ChatWS] Chunk received: {}", chunk);
                            if ("[DONE]".equals(chunk)) {
                                log.info("[ChatWS] DONE marker received");
                            } else if (chunk.startsWith("__THINKING__")) {
                                String thinking = chunk.substring("__THINKING__".length());
                                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(
                                    Map.of("type", "thinking", "content", thinking)
                                )));
                            } else {
                                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(
                                    Map.of("type", "chunk", "content", chunk)
                                )));
                            }
                        } catch (Exception e) {
                            log.error("[ChatWS] Failed to send chunk", e);
                        }
                    },
                    error -> {
                        log.error("[ChatWS] Stream error", error);
                        try {
                            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(
                                Map.of("type", "error", "content", "Stream error: " + error.getMessage())
                            )));
                        } catch (Exception e) {
                            log.error("[ChatWS] Failed to send error", e);
                        }
                    },
                    () -> {
                        try {
                            log.info("[ChatWS] Stream completed, sending done");
                            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(
                                Map.of("type", "done")
                            )));
                        } catch (Exception e) {
                            log.error("[ChatWS] Failed to send done", e);
                        }
                    }
                );
            } else {
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(
                    Map.of("type", "error", "content", "Unknown message type: " + type)
                )));
            }
        } catch (Exception e) {
            log.error("[ChatWS] Failed to process message", e);
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(
                Map.of("type", "error", "content", "Failed to process: " + e.getMessage())
            )));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        sessionWorkerMap.remove(session.getId());
        log.info("[ChatWS] Connection closed for session {}, status {}", session.getId(), status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("[ChatWS] Transport error for session {}", session.getId(), exception);
        sessionWorkerMap.remove(session.getId());
    }

    private String extractWorkerId(String path) {
        // Path format: /ws/chat/{workerId}
        String[] parts = path.split("/");
        if (parts.length >= 4) {
            String workerIdPart = parts[3];
            // Remove query string if present (e.g., "2?token=xxx" -> "2")
            int queryIndex = workerIdPart.indexOf('?');
            if (queryIndex > 0) {
                workerIdPart = workerIdPart.substring(0, queryIndex);
            }
            return workerIdPart;
        }
        return null;
    }
}
