package com.openclaw.orchestrator.controller;

import com.openclaw.orchestrator.dto.ChatMessageDto;
import com.openclaw.orchestrator.dto.ChatSessionDto;
import com.openclaw.orchestrator.entity.ChatMessage;
import com.openclaw.orchestrator.entity.ChatSession;
import com.openclaw.orchestrator.service.AgentRuntimeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173")
public class ChatSessionController {

    private final AgentRuntimeService agentRuntimeService;

    @GetMapping("/{workerId}/sessions")
    public List<ChatSessionDto> listSessions(@PathVariable Long workerId, @RequestParam(required = false) Boolean archived) {
        return agentRuntimeService.getSessions(workerId, archived).stream()
            .map(ChatSessionDto::fromEntity)
            .collect(Collectors.toList());
    }

    @PostMapping("/{workerId}/sessions")
    public ChatSessionDto createSession(@PathVariable Long workerId, @RequestBody(required = false) Map<String, String> body) {
        String title = body != null ? body.get("title") : null;
        return ChatSessionDto.fromEntity(agentRuntimeService.createSession(workerId, title));
    }

    @GetMapping("/{workerId}/sessions/{sessionId}/messages")
    public List<ChatMessageDto> getMessages(@PathVariable Long workerId, @PathVariable Long sessionId) {
        return agentRuntimeService.getMessages(sessionId).stream()
            .map(ChatMessageDto::fromEntity)
            .collect(Collectors.toList());
    }

    @DeleteMapping("/{workerId}/sessions/{sessionId}")
    public void deleteSession(@PathVariable Long workerId, @PathVariable Long sessionId) {
        agentRuntimeService.deleteSession(sessionId);
    }

    @PutMapping("/{workerId}/sessions/{sessionId}/title")
    public ChatSessionDto updateSessionTitle(@PathVariable Long workerId, @PathVariable Long sessionId, @RequestBody Map<String, String> body) {
        String title = body.get("title");
        return ChatSessionDto.fromEntity(agentRuntimeService.updateSessionTitle(sessionId, title));
    }

    @PutMapping("/{workerId}/sessions/{sessionId}/archive")
    public ChatSessionDto archiveSession(@PathVariable Long workerId, @PathVariable Long sessionId) {
        return ChatSessionDto.fromEntity(agentRuntimeService.archiveSession(sessionId));
    }

    @PutMapping("/{workerId}/sessions/{sessionId}/unarchive")
    public ChatSessionDto unarchiveSession(@PathVariable Long workerId, @PathVariable Long sessionId) {
        return ChatSessionDto.fromEntity(agentRuntimeService.unarchiveSession(sessionId));
    }
}
