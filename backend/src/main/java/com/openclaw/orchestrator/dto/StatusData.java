package com.openclaw.orchestrator.dto;

import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StatusData {
    private String type;          // "status_update"
    private LocalDateTime timestamp;
    private Long instanceId;
    private InstanceInfo instance;
    private String statusText;    // 详细状态文本，如 "🦞 使用模型: xxx"
    private List<SessionInfo> sessions;
    private List<AgentInfo> agents;
    private List<ActivityEvent> activities;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class InstanceInfo {
        private Long id;
        private String name;
        private String url;
        private String status;    // "online" / "offline"
        private LocalDateTime lastUpdate;
        private String tokenMasked; // 脱敏 token，如 "****abcd"
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SessionInfo {
        private String id;
        private String status;    // "active" / "error" / "failed"
        private String agentId;
        private LocalDateTime createdAt;
        private boolean abnormal; // true if status is "error" or "failed"
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AgentInfo {
        private String id;
        private String name;
        private String status;
        private int sessionCount;
    }
}
