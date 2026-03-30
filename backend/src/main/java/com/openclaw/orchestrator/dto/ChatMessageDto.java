package com.openclaw.orchestrator.dto;

import com.openclaw.orchestrator.entity.ChatMessage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessageDto {
    private Long id;
    private String role;
    private String content;
    private String thinking;
    private LocalDateTime createdAt;

    public static ChatMessageDto fromEntity(ChatMessage msg) {
        return ChatMessageDto.builder()
            .id(msg.getId())
            .role(msg.getRole())
            .content(msg.getContent())
            .thinking(msg.getThinking())
            .createdAt(msg.getCreatedAt())
            .build();
    }
}
