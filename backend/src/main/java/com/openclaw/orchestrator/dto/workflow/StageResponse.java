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
public class StageResponse {
    private Long id;
    private Long workflowId;
    private String name;
    private String description;
    private Integer stageOrder;
    private String taskType;
    private Long workerId;
    private String workerName;
    private Long approverId;
    private String approverName;
    private Long outputSchemaId;
    private String outputSchemaName;
    private Integer maxRetries;
    private Integer priority;
    private Long nextStageId;
    private String conditionExpr;
    private List<BranchResponse> branches;
    private Integer x;
    private Integer y;
    private LocalDateTime createdAt;
}
