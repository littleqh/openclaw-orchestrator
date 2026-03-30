package com.openclaw.orchestrator.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LLMModelTestResponse {
    private Boolean ok;
    private Long latencyMs;
    private String error;
}
