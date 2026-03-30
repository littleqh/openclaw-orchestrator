package com.openclaw.orchestrator.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openclaw.orchestrator.dto.LLMChunk;
import com.openclaw.orchestrator.entity.ChatMessage;
import com.openclaw.orchestrator.entity.ChatSession;
import com.openclaw.orchestrator.entity.LLMModel;
import com.openclaw.orchestrator.entity.Worker;
import com.openclaw.orchestrator.repository.ChatMessageRepository;
import com.openclaw.orchestrator.repository.ChatSessionRepository;
import com.openclaw.orchestrator.repository.LLMModelRepository;
import com.openclaw.orchestrator.repository.WorkerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
@RequiredArgsConstructor
@Slf4j
public class AgentRuntimeService {

    private final WorkerRepository workerRepository;
    private final LLMModelRepository llmModelRepository;
    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final LLMClientService llmClientService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // workerId -> sessionId -> Sinks.Many<String> for streaming responses
    private final Map<Long, Map<Long, Sinks.Many<String>>> sessionSinks = new ConcurrentHashMap<>();

    /**
     * Create a new chat session for a worker.
     */
    @Transactional
    public ChatSession createSession(Long workerId, String title) {
        Worker worker = workerRepository.findById(workerId)
            .orElseThrow(() -> new RuntimeException("NOT_FOUND:员工不存在"));

        if (!Boolean.TRUE.equals(worker.getLocalRuntime())) {
            throw new RuntimeException("该员工不是本地 Agent，无法聊天");
        }

        ChatSession session = ChatSession.builder()
            .worker(worker)
            .title(title != null ? title : "对话 " + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm")))
            .build();
        return chatSessionRepository.save(session);
    }

    /**
     * Get all sessions for a worker, optionally filtered by archived status.
     */
    public List<ChatSession> getSessions(Long workerId, Boolean archived) {
        if (archived == null) {
            return chatSessionRepository.findByWorkerIdOrderByUpdatedAtDesc(workerId);
        }
        return chatSessionRepository.findByWorkerIdAndArchivedOrderByUpdatedAtDesc(workerId, archived);
    }

    /**
     * Get all sessions for a worker (backward compatible).
     */
    public List<ChatSession> getSessions(Long workerId) {
        return getSessions(workerId, false);
    }

    /**
     * Get messages for a session.
     */
    public List<ChatMessage> getMessages(Long sessionId) {
        return chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
    }

    /**
     * Delete a session.
     */
    @Transactional
    public void deleteSession(Long sessionId) {
        ChatSession session = chatSessionRepository.findById(sessionId)
            .orElseThrow(() -> new RuntimeException("NOT_FOUND:会话不存在"));
        chatSessionRepository.delete(session);
    }

    /**
     * Update session title.
     */
    @Transactional
    public ChatSession updateSessionTitle(Long sessionId, String title) {
        ChatSession session = chatSessionRepository.findById(sessionId)
            .orElseThrow(() -> new RuntimeException("NOT_FOUND:会话不存在"));
        session.setTitle(title);
        return chatSessionRepository.save(session);
    }

    /**
     * Archive a session.
     */
    @Transactional
    public ChatSession archiveSession(Long sessionId) {
        ChatSession session = chatSessionRepository.findById(sessionId)
            .orElseThrow(() -> new RuntimeException("NOT_FOUND:会话不存在"));
        session.setArchived(true);
        return chatSessionRepository.save(session);
    }

    /**
     * Unarchive a session.
     */
    @Transactional
    public ChatSession unarchiveSession(Long sessionId) {
        ChatSession session = chatSessionRepository.findById(sessionId)
            .orElseThrow(() -> new RuntimeException("NOT_FOUND:会话不存在"));
        session.setArchived(false);
        return chatSessionRepository.save(session);
    }

    /**
     * Handle a chat message and return streaming response.
     * Returns Flux of content chunks.
     */
    @Transactional
    public Flux<String> chat(Long workerId, Long sessionId, String userMessage) {
        // Get worker and validate
        Worker worker = workerRepository.findById(workerId)
            .orElseThrow(() -> new RuntimeException("NOT_FOUND:员工不存在"));

        if (!Boolean.TRUE.equals(worker.getLocalRuntime())) {
            return Flux.just("【错误：该员工不是本地 Agent，无法聊天】");
        }

        // Get model
        LLMModel model = worker.getModel();
        log.info("[AgentRuntime] workerId={}, sessionId={}, model={}", workerId, sessionId, model != null ? model.getName() : "null");
        if (model == null) {
            return Flux.just("【错误：该员工未配置模型】");
        }

        // Get or create session
        ChatSession session;
        if (sessionId != null) {
            session = chatSessionRepository.findById(sessionId).orElse(null);
        } else {
            session = createSession(workerId, "新对话");
        }

        if (session == null) {
            return Flux.just("【错误：会话不存在】");
        }

        // Save user message
        ChatMessage userMsg = ChatMessage.builder()
            .role("user")
            .content(userMessage)
            .build();
        session.addMessage(userMsg);
        chatMessageRepository.save(userMsg);

        // Build messages list
        List<Map<String, String>> messages = buildMessages(worker, session, userMessage);

        // Create sink for streaming
        Sinks.Many<String> sink = Sinks.many().multicast().onBackpressureBuffer();
        sessionSinks.computeIfAbsent(workerId, k -> new ConcurrentHashMap<>()).put(session.getId(), sink);

        // Call LLM in background and send chunks to sink
        new Thread(() -> {
            try {
                log.info("[AgentRuntime] Calling llmClientService.streamChat for worker={}, session={}", workerId, session.getId());
                Flux<LLMChunk> stream = llmClientService.streamChat(model, messages);
                log.info("[AgentRuntime] Stream obtained, subscribing...");

                StringBuilder fullResponse = new StringBuilder();
                StringBuilder fullThinking = new StringBuilder();

                stream.subscribe(
                    chunk -> {
                        log.info("[AgentRuntime] LLM chunk: content={}, thinking={}", chunk.getContent(), chunk.getThinking());
                        // Handle [DONE] marker
                        if ("[DONE]".equals(chunk.getContent())) {
                            return;
                        }
                        // Send content to frontend
                        if (chunk.getContent() != null && !chunk.getContent().isEmpty()) {
                            fullResponse.append(chunk.getContent());
                            try {
                                sink.tryEmitNext(chunk.getContent());
                            } catch (Exception e) {
                                log.error("[AgentRuntime] Failed to send content chunk", e);
                            }
                        }
                        // Send thinking to frontend (as separate message type)
                        if (chunk.getThinking() != null && !chunk.getThinking().isEmpty()) {
                            fullThinking.append(chunk.getThinking());
                            try {
                                sink.tryEmitNext("__THINKING__" + chunk.getThinking());
                            } catch (Exception e) {
                                log.error("[AgentRuntime] Failed to send thinking chunk", e);
                            }
                        }
                    },
                    error -> {
                        log.error("LLM stream error for worker {} session {}", workerId, session.getId(), error);
                        try {
                            sink.tryEmitNext(objectMapper.writeValueAsString(Map.of("type", "error", "content", "LLM 调用失败: " + error.getMessage())));
                        } catch (Exception e) {}
                    },
                    () -> {
                        log.info("[AgentRuntime] Stream completed for worker {} session {}", workerId, session.getId());
                        // Save assistant message with both content and thinking
                        ChatMessage assistantMsg = ChatMessage.builder()
                            .role("assistant")
                            .content(fullResponse.toString())
                            .thinking(fullThinking.length() > 0 ? fullThinking.toString() : null)
                            .build();
                        session.addMessage(assistantMsg);
                        chatMessageRepository.save(assistantMsg);

                        try {
                            sink.tryEmitNext(objectMapper.writeValueAsString(Map.of("type", "done")));
                        } catch (Exception e) {}
                        cleanupSink(workerId, session.getId());
                    }
                );
            } catch (Exception e) {
                log.error("LLM chat error for worker {} session {}", workerId, session.getId(), e);
                try {
                    sink.tryEmitNext(objectMapper.writeValueAsString(Map.of("type", "error", "content", "错误：" + e.getMessage())));
                    sink.tryEmitNext(objectMapper.writeValueAsString(Map.of("type", "done")));
                } catch (Exception ex) {}
                cleanupSink(workerId, session.getId());
            }
        }).start();

        return sink.asFlux();
    }

    /**
     * Get the sink for a specific session (for WebSocket to subscribe).
     */
    public Sinks.Many<String> getSink(Long workerId, Long sessionId) {
        Map<Long, Sinks.Many<String>> workerSinks = sessionSinks.get(workerId);
        if (workerSinks == null) {
            return null;
        }
        return workerSinks.get(sessionId);
    }

    private List<Map<String, String>> buildMessages(Worker worker, ChatSession session, String userMessage) {
        List<Map<String, String>> messages = new ArrayList<>();

        // System message
        String systemPrompt = worker.getSystemPrompt();
        String personality = worker.getPersonality();
        StringBuilder systemContent = new StringBuilder();
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            systemContent.append(systemPrompt);
        }
        if (personality != null && !personality.isEmpty()) {
            if (systemContent.length() > 0) {
                systemContent.append("\n\n");
            }
            systemContent.append("人格特点：").append(personality);
        }
        if (systemContent.length() > 0) {
            messages.add(Map.of("role", "system", "content", systemContent.toString()));
        }

        // Previous messages
        for (ChatMessage msg : session.getMessages()) {
            if (!msg.getRole().equals("user") && !msg.getRole().equals("assistant")) {
                continue; // Skip system messages already handled
            }
            if (msg.getContent() != null && !msg.getContent().isEmpty()) {
                messages.add(Map.of("role", msg.getRole(), "content", msg.getContent()));
            }
        }

        // Current user message
        messages.add(Map.of("role", "user", "content", userMessage));

        return messages;
    }

    private void cleanupSink(Long workerId, Long sessionId) {
        Map<Long, Sinks.Many<String>> workerSinks = sessionSinks.get(workerId);
        if (workerSinks != null) {
            workerSinks.remove(sessionId);
            if (workerSinks.isEmpty()) {
                sessionSinks.remove(workerId);
            }
        }
    }
}
