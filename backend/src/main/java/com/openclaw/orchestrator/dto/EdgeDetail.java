package com.openclaw.orchestrator.dto;

import lombok.Data;
import lombok.Builder;

@Data
@Builder
public class EdgeDetail {
    private Long id;
    private Long sourceNodeId;
    private Long targetNodeId;
}
