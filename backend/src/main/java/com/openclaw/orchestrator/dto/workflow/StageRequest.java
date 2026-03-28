package com.openclaw.orchestrator.dto.workflow;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StageRequest {
    private Long id;
    private String name;
    private String description;
    private Integer stageOrder;
    private String taskType; // AUTO or APPROVAL
    private Long workerId;
    private Long approverId;
    private Long outputSchemaId;
    private Integer maxRetries;
    private Integer priority;
    private Long nextStageId;
    private String conditionExpr;
    private List<BranchRequest> branches;
    private Integer x;  // 节点 x 坐标
    private Integer y;  // 节点 y 坐标
}
