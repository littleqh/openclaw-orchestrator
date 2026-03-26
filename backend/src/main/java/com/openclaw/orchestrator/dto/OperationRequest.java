package com.openclaw.orchestrator.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.util.List;

@Data
public class OperationRequest {
    @NotBlank(message = "操作名称不能为空")
    @Size(max = 50, message = "操作名称不能超过50字符")
    private String name;

    @Size(max = 500, message = "描述不能超过500字符")
    private String description;

    private List<Long> skillIds;
}
