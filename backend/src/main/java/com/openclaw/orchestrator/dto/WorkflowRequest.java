package com.openclaw.orchestrator.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.util.List;

@Data
public class WorkflowRequest {
    @NotBlank(message = "名称不能为空")
    @Size(max = 100, message = "名称不能超过100字符")
    private String name;

    @Size(max = 500, message = "描述不能超过500字符")
    private String description;

    private Long version;

    @Valid
    private List<NodeRequest> nodes;

    @Valid
    private List<EdgeRequest> edges;
}
