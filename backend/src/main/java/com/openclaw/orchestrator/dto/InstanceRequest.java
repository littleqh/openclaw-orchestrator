package com.openclaw.orchestrator.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class InstanceRequest {
    @NotBlank(message = "名称不能为空")
    private String name;

    @NotBlank(message = "URL不能为空")
    private String url;

    @NotBlank(message = "Token不能为空")
    private String token;

    private String description;
}
