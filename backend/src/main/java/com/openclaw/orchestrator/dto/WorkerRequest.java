package com.openclaw.orchestrator.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.util.List;

@Data
public class WorkerRequest {
    @NotBlank(message = "姓名不能为空")
    @Size(max = 50, message = "姓名不能超过50字符")
    private String name;

    @Size(max = 50, message = "昵称不能超过50字符")
    private String nickname;

    @Size(max = 100, message = "角色不能超过100字符")
    private String role;

    @Size(max = 500, message = "Gateway地址不能超过500字符")
    private String gatewayUrl;

    @Size(max = 500, message = "密钥不能超过500字符")
    private String gatewayToken;

    @Size(max = 2000, message = "人格描述不能超过2000字符")
    private String personality;

    private String status;

    @Size(max = 500, message = "头像URL不能超过500字符")
    private String avatar;

    private List<Long> skillIds;

    private Boolean localRuntime;

    private Long modelId;

    @Size(max = 4000, message = "系统提示不能超过4000字符")
    private String systemPrompt;
}