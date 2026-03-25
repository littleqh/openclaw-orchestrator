package com.openclaw.orchestrator.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class EdgeRequest {
    @NotBlank(message = "源节点ID不能为空")
    private String sourceTempId;

    @NotBlank(message = "目标节点ID不能为空")
    private String targetTempId;
}
