package com.openclaw.orchestrator.dto;

import com.openclaw.orchestrator.entity.ChatSession;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatSessionDto {
    private Long id;
    private Long workerId;
    private String title;
    private List<ChatMessageDto> messages;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Boolean archived;

    public static ChatSessionDto fromEntity(ChatSession session) {
        return ChatSessionDto.builder()
            .id(session.getId())
            .workerId(session.getWorker().getId())
            .title(session.getTitle())
            .createdAt(session.getCreatedAt())
            .updatedAt(session.getUpdatedAt())
            .archived(session.getArchived())
            .build();
    }
}
