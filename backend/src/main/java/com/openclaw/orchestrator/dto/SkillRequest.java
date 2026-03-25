package com.openclaw.orchestrator.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SkillRequest {
    @NotBlank(message = "技能名称不能为空")
    @Size(max = 50, message = "技能名称不能超过50字符")
    private String name;

    @Size(max = 500, message = "技能描述不能超过500字符")
    private String description;
}