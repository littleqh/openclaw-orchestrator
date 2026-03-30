package com.openclaw.orchestrator.dto;

import com.openclaw.orchestrator.entity.LLMModel;
import lombok.*;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LLMModelDetail {
    private Long id;
    private String name;
    private String provider;
    private String model;
    private String baseUrl;
    private Integer maxTokens;
    private Boolean enabled;
    private String apiFormat;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static LLMModelDetail fromEntity(LLMModel m) {
        return LLMModelDetail.builder()
            .id(m.getId())
            .name(m.getName())
            .provider(m.getProvider())
            .model(m.getModel())
            .baseUrl(m.getBaseUrl())
            .maxTokens(m.getMaxTokens())
            .enabled(m.getEnabled())
            .apiFormat(m.getApiFormat())
            .createdAt(m.getCreatedAt())
            .updatedAt(m.getUpdatedAt())
            .build();
    }
}
