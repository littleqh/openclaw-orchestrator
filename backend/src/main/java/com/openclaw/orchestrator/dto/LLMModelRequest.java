package com.openclaw.orchestrator.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LLMModelRequest {
    @NotBlank(message = "模型名称不能为空")
    @Size(max = 50, message = "模型名称不能超过50字符")
    private String name;

    @NotBlank(message = "Provider不能为空")
    @Size(max = 20, message = "Provider不能超过20字符")
    private String provider;

    @NotBlank(message = "模型不能为空")
    @Size(max = 100, message = "模型名不能超过100字符")
    private String model;

    @Size(max = 500, message = "Base URL不能超过500字符")
    private String baseUrl;

    @Size(max = 500, message = "API Key不能超过500字符")
    private String apiKey;

    private Integer maxTokens;

    private Boolean enabled = true;

    private String apiFormat;
}
