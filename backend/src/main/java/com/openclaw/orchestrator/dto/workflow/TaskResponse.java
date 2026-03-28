package com.openclaw.orchestrator.dto.workflow;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskResponse {
    private Long id;
    private Long instanceId;
    private Long stageId;
    private String stageName;
    private String stageDescription;
    private Long workerId;
    private String workerName;
    private String type;
    private String status;
    private Integer priority;
    private Integer retryCount;
    private Integer maxRetries;
    private String output;
    private String outputSchemaName;
    private Long approverId;
    private String approverName;
    private String approverComments;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
