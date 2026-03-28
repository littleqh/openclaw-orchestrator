package com.openclaw.orchestrator.dto.workflow;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowResponse {
    private Long id;
    private String name;
    private String description;
    private Integer defaultMaxRetries;
    private Boolean isActive;
    private Boolean isSystem;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<StageResponse> stages;
}
