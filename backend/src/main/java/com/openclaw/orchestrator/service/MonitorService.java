package com.openclaw.orchestrator.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openclaw.orchestrator.dto.ActivityEvent;
import com.openclaw.orchestrator.dto.StatusData;
import com.openclaw.orchestrator.dto.StatusData.AgentInfo;
import com.openclaw.orchestrator.dto.StatusData.InstanceInfo;
import com.openclaw.orchestrator.dto.StatusData.SessionInfo;
import com.openclaw.orchestrator.entity.GatewayInstance;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class MonitorService {

    private final GatewayService gatewayService;
    private final SseEmitterService sseEmitterService;

    // Polling interval: 15 seconds
    private static final long POLLING_INTERVAL_MS = 15_000;
    // Keepalive interval: 12 seconds
    private static final long KEEPALIVE_INTERVAL_MS = 12_000;

    // instanceId -> latest cached StatusData
    private final Map<Long, StatusData> statusCache = new ConcurrentHashMap<>();
    // instanceId -> activity list (max 20 per instance)
    private final Map<Long, List<ActivityEvent>> activityCache = new ConcurrentHashMap<>();
    // instanceId -> last known session IDs (to detect session_created/ended)
    private final Map<Long, Set<String>> lastSessionIds = new ConcurrentHashMap<>();
    // instanceId -> last known agent IDs (to detect agent_registered/unregistered)
    private final Map<Long, Set<String>> lastAgentIds = new ConcurrentHashMap<>();

    private volatile boolean shuttingDown = false;

    // ── SSE Endpoint 调用 ──────────────────────────────────────

    /**
     * 获取当前缓存的状态，连接 SSE 时立即推送。
     */
    public Optional<StatusData> getCachedStatus(Long instanceId) {
        return Optional.ofNullable(statusCache.get(instanceId));
    }

    // ── 定时任务 ──────────────────────────────────────

    @Scheduled(fixedRate = POLLING_INTERVAL_MS)
    public void pollAllInstances() {
        if (shuttingDown) return;

        List<GatewayInstance> instances = gatewayService.listInstances();
        for (GatewayInstance instance : instances) {
            try {
                pollInstance(instance);
            } catch (Exception e) {
                log.error("轮询实例 {} 失败: {}", instance.getId(), e.getMessage());
                broadcastOffline(instance);
            }
        }
    }

    @Scheduled(fixedRate = KEEPALIVE_INTERVAL_MS)
    public void sendKeepalive() {
        if (shuttingDown) return;

        for (Long instanceId : statusCache.keySet()) {
            try {
                sseEmitterService.sendHeartbeat(instanceId);
            } catch (Exception e) {
                log.debug("Keepalive failed for instance {}: {}", instanceId, e.getMessage());
            }
        }
    }

    // ── 轮询逻辑 ──────────────────────────────────────

    private void pollInstance(GatewayInstance instance) {
        Long instanceId = instance.getId();
        boolean wasOffline = isInstanceOffline(instanceId);

        // 调用 Gateway API
        JsonNode statusResponse = gatewayService.getStatus(instance);
        JsonNode sessionsResponse = gatewayService.getSessions(instance);
        JsonNode subagentsResponse = gatewayService.getSubagents(instance);

        // 解析 statusText
        String statusText = parseStatusText(statusResponse);

        // 解析 sessions
        List<SessionInfo> sessions = parseSessions(sessionsResponse);
        Set<String> currentSessionIds = new HashSet<>();
        for (SessionInfo s : sessions) {
            currentSessionIds.add(s.getId());
        }

        // 解析 agents
        List<AgentInfo> agents = parseAgents(subagentsResponse);
        Set<String> currentAgentIds = new HashSet<>();
        for (AgentInfo a : agents) {
            currentAgentIds.add(a.getId());
        }

        // 检测活动
        detectAndRecordActivities(instanceId, sessions, agents);

        // 构建 StatusData
        InstanceInfo instanceInfo = InstanceInfo.builder()
                .id(instance.getId())
                .name(instance.getName())
                .url(instance.getUrl())
                .status("online")
                .lastUpdate(LocalDateTime.now())
                .tokenMasked(maskToken(instance.getToken()))
                .build();

        List<ActivityEvent> activities = activityCache.getOrDefault(instanceId, Collections.emptyList());

        StatusData statusData = StatusData.builder()
                .type("status_update")
                .timestamp(LocalDateTime.now())
                .instanceId(instanceId)
                .instance(instanceInfo)
                .statusText(statusText)
                .sessions(sessions)
                .agents(agents)
                .activities(new ArrayList<>(activities))
                .build();

        // 更新缓存
        statusCache.put(instanceId, statusData);
        lastSessionIds.put(instanceId, currentSessionIds);
        lastAgentIds.put(instanceId, currentAgentIds);

        // 广播给 SSE 订阅者
        sseEmitterService.broadcast(instanceId, statusData);

        // 如果之前是 offline 状态，恢复时通知
        if (wasOffline) {
            log.info("Instance {} recovered to online", instanceId);
        }
    }

    private List<SessionInfo> parseSessions(JsonNode response) {
        List<SessionInfo> result = new ArrayList<>();
        try {
            if (response == null || !response.has("ok") || !response.get("ok").asBoolean()) {
                return result;
            }
            JsonNode details = response.path("result").path("details");
            JsonNode sessions = details.path("sessions");
            if (sessions.isArray()) {
                for (JsonNode s : sessions) {
                    String status = s.path("status").asText("unknown");
                    boolean abnormal = "error".equalsIgnoreCase(status) || "failed".equalsIgnoreCase(status);
                    result.add(SessionInfo.builder()
                            .id(s.path("id").asText("unknown"))
                            .status(status)
                            .agentId(s.path("agentId").asText("unknown"))
                            .createdAt(parseDateTime(s.path("createdAt")))
                            .abnormal(abnormal)
                            .build());
                }
            }
        } catch (Exception e) {
            log.error("解析 sessions 失败: {}", e.getMessage());
        }
        return result;
    }

    private String parseStatusText(JsonNode response) {
        try {
            log.info("[MonitorService] session_status raw response: {}", response);
            if (response == null || !response.has("ok") || !response.get("ok").asBoolean()) {
                return null;
            }
            JsonNode details = response.path("result").path("details");
            String statusText = details.path("statusText").asText(null);
            log.info("[MonitorService] statusText extracted: {}", statusText);
            return statusText;
        } catch (Exception e) {
            log.error("解析 statusText 失败: {}", e.getMessage());
            return null;
        }
    }

    private List<AgentInfo> parseAgents(JsonNode response) {
        List<AgentInfo> result = new ArrayList<>();
        try {
            if (response == null || !response.has("ok") || !response.get("ok").asBoolean()) {
                return result;
            }
            JsonNode details = response.path("result").path("details");
            JsonNode active = details.path("active");
            if (active.isArray()) {
                for (JsonNode a : active) {
                    result.add(AgentInfo.builder()
                            .id(a.path("id").asText("unknown"))
                            .name(a.path("name").asText(a.path("id").asText("unknown")))
                            .status(a.path("status").asText("unknown"))
                            .sessionCount(a.path("sessionCount").asInt(0))
                            .build());
                }
            }
        } catch (Exception e) {
            log.error("解析 agents 失败: {}", e.getMessage());
        }
        return result;
    }

    private void detectAndRecordActivities(Long instanceId, List<SessionInfo> sessions, List<AgentInfo> agents) {
        Set<String> prevSessionIds = lastSessionIds.getOrDefault(instanceId, Collections.emptySet());
        Set<String> prevAgentIds = lastAgentIds.getOrDefault(instanceId, Collections.emptySet());

        Set<String> currentSessionIds = new HashSet<>();
        for (SessionInfo s : sessions) {
            currentSessionIds.add(s.getId());
        }

        Set<String> currentAgentIds = new HashSet<>();
        for (AgentInfo a : agents) {
            currentAgentIds.add(a.getId());
        }

        List<ActivityEvent> events = activityCache.computeIfAbsent(instanceId, k -> new ArrayList<>());

        // 检测新增 session
        for (String newId : currentSessionIds) {
            if (!prevSessionIds.contains(newId)) {
                SessionInfo s = sessions.stream().filter(x -> x.getId().equals(newId)).findFirst().orElse(null);
                if (s != null) {
                    events.add(ActivityEvent.builder()
                            .time(LocalDateTime.now())
                            .type(ActivityEvent.SESSION_CREATED)
                            .detail("Session " + s.getId() + " created, status: " + s.getStatus())
                            .build());
                }
            }
        }

        // 检测消失的 session
        for (String oldId : prevSessionIds) {
            if (!currentSessionIds.contains(oldId)) {
                events.add(ActivityEvent.builder()
                        .time(LocalDateTime.now())
                        .type(ActivityEvent.SESSION_ENDED)
                        .detail("Session " + oldId + " ended")
                        .build());
            }
        }

        // 检测新增 agent
        for (String newId : currentAgentIds) {
            if (!prevAgentIds.contains(newId)) {
                events.add(ActivityEvent.builder()
                        .time(LocalDateTime.now())
                        .type(ActivityEvent.AGENT_REGISTERED)
                        .detail("Agent " + newId + " registered")
                        .build());
            }
        }

        // 检测消失的 agent
        for (String oldId : prevAgentIds) {
            if (!currentAgentIds.contains(oldId)) {
                events.add(ActivityEvent.builder()
                        .time(LocalDateTime.now())
                        .type(ActivityEvent.AGENT_UNREGISTERED)
                        .detail("Agent " + oldId + " unregistered")
                        .build());
            }
        }

        // 保留最近 20 条
        while (events.size() > 20) {
            events.remove(0);
        }
    }

    private void broadcastOffline(GatewayInstance instance) {
        Long instanceId = instance.getId();

        InstanceInfo instanceInfo = InstanceInfo.builder()
                .id(instance.getId())
                .name(instance.getName())
                .url(instance.getUrl())
                .status("offline")
                .lastUpdate(LocalDateTime.now())
                .tokenMasked(maskToken(instance.getToken()))
                .build();

        StatusData offlineData = StatusData.builder()
                .type("status_update")
                .timestamp(LocalDateTime.now())
                .instanceId(instanceId)
                .instance(instanceInfo)
                .statusText(null)
                .sessions(Collections.emptyList())
                .agents(Collections.emptyList())
                .activities(new ArrayList<>(activityCache.getOrDefault(instanceId, Collections.emptyList())))
                .build();

        statusCache.put(instanceId, offlineData);
        sseEmitterService.broadcast(instanceId, offlineData);
    }

    private boolean isInstanceOffline(Long instanceId) {
        StatusData cached = statusCache.get(instanceId);
        return cached != null && "offline".equals(cached.getInstance().getStatus());
    }

    private String maskToken(String token) {
        if (token == null || token.length() <= 4) {
            return "****";
        }
        return "****" + token.substring(token.length() - 4);
    }

    private LocalDateTime parseDateTime(JsonNode node) {
        if (node.isNull() || !node.isTextual()) {
            return LocalDateTime.now();
        }
        try {
            return LocalDateTime.parse(node.asText());
        } catch (Exception e) {
            return LocalDateTime.now();
        }
    }

    public void shutdown() {
        shuttingDown = true;
    }
}
