package com.openclaw.orchestrator.dto;

import lombok.Data;

@Data
public class TokenCreateRequest {
    private Long workerId; // null for system token
}