package com.openclaw.orchestrator.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenResponse {
    private Long id;
    private String token;        // Full token, only on create/reset
    private String tokenPreview; // Masked token, e.g. "****-****-****-a716"
    private String type;        // "SYSTEM" or "AGENT"
    private Long workerId;
    private String workerName;
    private LocalDateTime createdAt;
    private LocalDateTime lastAccessAt;
}