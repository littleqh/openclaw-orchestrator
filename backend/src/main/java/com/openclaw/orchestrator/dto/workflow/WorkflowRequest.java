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
public class WorkflowRequest {
    private String name;
    private String description;
    private Integer defaultMaxRetries;
    private List<StageRequest> stages;
}
