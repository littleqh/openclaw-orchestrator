package com.openclaw.orchestrator.dto.workflow;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InstanceResponse {
    private Long id;
    private Long workflowId;
    private String workflowName;
    private String status;
    private String description;
    private Long currentStageId;
    private String currentStageName;
    private Map<String, Object> variables;
    private Long startedBy;
    private String startedByName;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime updatedAt;
    private List<TaskResponse> tasks;
}
