package com.openclaw.orchestrator.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class NodeRequest {
    private String tempId;

    @NotNull(message = "员工ID不能为空")
    private Long workerId;

    @NotNull(message = "坐标X不能为空")
    private Double x;

    @NotNull(message = "坐标Y不能为空")
    private Double y;
}
