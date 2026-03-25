package com.openclaw.orchestrator.dto;

import lombok.Data;
import lombok.Builder;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class WorkflowDetail {
    private Long id;
    private String name;
    private String description;
    private Long version;
    private List<NodeDetail> nodes;
    private List<EdgeDetail> edges;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
