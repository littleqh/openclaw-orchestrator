# 实时 Agent/Session 监控系统实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现实时 Agent/Session 监控页面，左侧实例列表，右侧 4 面板展示（实例概览/Session列表/Agent列表/最近活动），通过 SSE 实时推送数据，异常项红色高亮。

**Architecture:**
- 后端：Spring Boot SSE (SseEmitter) + @Scheduled 定时轮询 Gateway，轮询间隔 15 秒，SSE keepalive 每 12 秒
- 前端：Vue 3 + Naive UI，新建 MonitorView 页面，左侧实例列表 + 右侧 4 面板网格布局
- 数据流：前端连接 SSE → 后端立即推送缓存数据 → 每 15 秒轮询 Gateway → 变化时推送

**Tech Stack:**
- Backend: Spring Boot 3.2.5, Spring WebFlux WebClient, SseEmitter
- Frontend: Vue 3, Naive UI, Native EventSource API

---

## 文件结构

### 后端新建

| 文件 | 职责 |
|-----|------|
| `backend/src/main/java/.../dto/StatusData.java` | SSE 推送的状态数据结构 DTO |
| `backend/src/main/java/.../dto/ActivityEvent.java` | 活动事件 DTO |
| `backend/src/main/java/.../service/SseEmitterService.java` | SSE 连接管理，单例管理所有 SSE 连接 |
| `backend/src/main/java/.../service/MonitorService.java` | 监控状态缓存、活动记录、定时轮询 |
| `backend/src/main/java/.../controller/SseStatusController.java` | SSE 端点 `/api/sse/status/{instanceId}` |

### 后端修改

| 文件 | 修改内容 |
|-----|---------|
| `backend/src/main/java/.../OrchestratorApplication.java` | 添加 `@EnableScheduling` |
| `backend/src/main/java/.../controller/InstanceController.java` | 无需修改（已有端点够用） |

### 前端新建

| 文件 | 职责 |
|-----|------|
| `frontend/src/views/MonitorView.vue` | 主监控页面，左侧实例列表 + 右侧 4 面板 |
| `frontend/src/components/OverviewPanel.vue` | 实例概览面板 |
| `frontend/src/components/SessionListPanel.vue` | Session 列表面板 |
| `frontend/src/components/AgentListPanel.vue` | Agent 列表面板 |
| `frontend/src/components/ActivityPanel.vue` | 最近活动面板 |
| `frontend/src/composables/useSse.js` | SSE 连接逻辑 + 重连机制 |

### 前端修改

| 文件 | 修改内容 |
|-----|---------|
| `frontend/src/App.vue` | 添加 Monitor 标签页 |
| `frontend/src/api/index.js` | 添加 `connectSse(instanceId)` 函数 |

---

## Task 1: 后端 DTO 创建

**Files:**
- Create: `backend/src/main/java/com/openclaw/orchestrator/dto/StatusData.java`
- Create: `backend/src/main/java/com/openclaw/orchestrator/dto/ActivityEvent.java`

- [ ] **Step 1: 创建 StatusData.java**

```java
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
```

- [ ] **Step 2: 创建 ActivityEvent.java**

```java
package com.openclaw.orchestrator.dto;

import lombok.*;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActivityEvent {
    private LocalDateTime time;
    private String type;   // session_created, session_ended, session_error, agent_registered, agent_unregistered
    private String detail;

    public static final String SESSION_CREATED = "session_created";
    public static final String SESSION_ENDED = "session_ended";
    public static final String SESSION_ERROR = "session_error";
    public static final String AGENT_REGISTERED = "agent_registered";
    public static final String AGENT_UNREGISTERED = "agent_unregistered";
}
```

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/openclaw/orchestrator/dto/StatusData.java backend/src/main/java/com/openclaw/orchestrator/dto/ActivityEvent.java
git commit -m "feat(backend): add StatusData and ActivityEvent DTOs"
```

---

## Task 2: SseEmitterService - SSE 连接管理

**Files:**
- Create: `backend/src/main/java/com/openclaw/orchestrator/service/SseEmitterService.java`

- [ ] **Step 1: 创建 SseEmitterService.java**

```java
package com.openclaw.orchestrator.service;

import com.openclaw.orchestrator.dto.StatusData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
@Slf4j
public class SseEmitterService {

    // instanceId -> List of SSE emitters for that instance
    private final Map<Long, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    // SSE timeout: 30 seconds (Spring default)
    private static final long SSE_TIMEOUT = 30_000L;

    /**
     * Register a new SSE connection for an instance.
     * Returns the SseEmitter for the caller to use.
     */
    public SseEmitter subscribe(Long instanceId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);

        emitters.computeIfAbsent(instanceId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> {
            log.debug("SSE connection completed for instance {}", instanceId);
            removeEmitter(instanceId, emitter);
        });
        emitter.onTimeout(() -> {
            log.debug("SSE connection timeout for instance {}", instanceId);
            removeEmitter(instanceId, emitter);
        });
        emitter.onError(e -> {
            log.debug("SSE connection error for instance {}: {}", instanceId, e.getMessage());
            removeEmitter(instanceId, emitter);
        });

        log.info("New SSE subscription for instance {}, total connections: {}",
                instanceId, emitters.get(instanceId).size());

        return emitter;
    }

    /**
     * Send StatusData to all subscribers of an instance.
     */
    public void broadcast(Long instanceId, StatusData data) {
        List<SseEmitter> list = emitters.get(instanceId);
        if (list == null || list.isEmpty()) {
            return;
        }

        List<SseEmitter> dead = new java.util.ArrayList<>();

        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event()
                        .name("status")
                        .data(data));
            } catch (IOException e) {
                log.debug("Failed to send to emitter, will remove: {}", e.getMessage());
                dead.add(emitter);
            }
        }

        // Clean up dead emitters
        for (SseEmitter deadEmitter : dead) {
            removeEmitter(instanceId, deadEmitter);
        }
    }

    /**
     * Send keepalive comment to keep connection alive.
     */
    public void sendHeartbeat(Long instanceId) {
        List<SseEmitter> list = emitters.get(instanceId);
        if (list == null) return;

        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event().comment("keepalive"));
            } catch (IOException e) {
                // ignore, will be cleaned up on next broadcast
            }
        }
    }

    /**
     * Get number of active connections for an instance.
     */
    public int getConnectionCount(Long instanceId) {
        List<SseEmitter> list = emitters.get(instanceId);
        return list == null ? 0 : list.size();
    }

    private void removeEmitter(Long instanceId, SseEmitter emitter) {
        List<SseEmitter> list = emitters.get(instanceId);
        if (list != null) {
            list.remove(emitter);
            if (list.isEmpty()) {
                emitters.remove(instanceId);
            }
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add backend/src/main/java/com/openclaw/orchestrator/service/SseEmitterService.java
git commit -m "feat(backend): add SseEmitterService for SSE connection management"
```

---

## Task 3: MonitorService - 监控状态缓存和定时轮询

**Files:**
- Create: `backend/src/main/java/com/openclaw/orchestrator/service/MonitorService.java`

- [ ] **Step 1: 创建 MonitorService.java**

```java
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
    private final ObjectMapper objectMapper = new ObjectMapper();

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
        JsonNode sessionsResponse = gatewayService.getSessions(instance);
        JsonNode subagentsResponse = gatewayService.getSubagents(instance);

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
                .sessions(Collections.emptyList())
                .agents(Collections.emptyList())
                .activities(activityCache.getOrDefault(instanceId, Collections.emptyList()))
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
```

- [ ] **Step 2: Commit**

```bash
git add backend/src/main/java/com/openclaw/orchestrator/service/MonitorService.java
git commit -m "feat(backend): add MonitorService with 15s polling and SSE broadcast"
```

---

## Task 4: SseStatusController - SSE 端点

**Files:**
- Create: `backend/src/main/java/com/openclaw/orchestrator/controller/SseStatusController.java`

- [ ] **Step 1: 创建 SseStatusController.java**

```java
package com.openclaw.orchestrator.controller;

import com.openclaw.orchestrator.dto.StatusData;
import com.openclaw.orchestrator.service.MonitorService;
import com.openclaw.orchestrator.service.SseEmitterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Optional;

@RestController
@RequestMapping("/api/sse")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "http://localhost:5173")
public class SseStatusController {

    private final SseEmitterService sseEmitterService;
    private final MonitorService monitorService;

    /**
     * SSE endpoint for real-time status updates.
     * GET /api/sse/status/{instanceId}
     */
    @GetMapping(value = "/status/{instanceId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(@PathVariable Long instanceId) {
        log.info("SSE subscription request for instance {}", instanceId);

        // 1. 创建 SSE 连接
        SseEmitter emitter = sseEmitterService.subscribe(instanceId);

        // 2. 立即推送缓存的最新状态（如果有）
        Optional<StatusData> cached = monitorService.getCachedStatus(instanceId);
        if (cached.isPresent()) {
            try {
                emitter.send(SseEmitter.event()
                        .name("status")
                        .data(cached.get()));
            } catch (Exception e) {
                log.debug("Failed to send cached status on connect: {}", e.getMessage());
            }
        }

        return emitter;
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add backend/src/main/java/com/openclaw/orchestrator/controller/SseStatusController.java
git commit -m "feat(backend): add SseStatusController for SSE endpoint"
```

---

## Task 5: 启用定时任务

**Files:**
- Modify: `backend/src/main/java/com/openclaw/orchestrator/OrchestratorApplication.java`

- [ ] **Step 1: 添加 @EnableScheduling**

```java
package com.openclaw.orchestrator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class OrchestratorApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrchestratorApplication.class, args);
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add backend/src/main/java/com/openclaw/orchestrator/OrchestratorApplication.java
git commit -m "feat(backend): enable scheduling for periodic polling"
```

---

## Task 6: 前端 API - SSE 连接函数

**Files:**
- Modify: `frontend/src/api/index.js`

- [ ] **Step 1: 添加 connectSse 函数**

在 `export const instanceApi` 之后添加：

```javascript
// SSE 连接
export function connectSse(instanceId, onMessage, onError, onClose) {
  const url = `/api/sse/status/${instanceId}`
  const eventSource = new EventSource(url)

  eventSource.addEventListener('status', (event) => {
    try {
      const data = JSON.parse(event.data)
      onMessage(data)
    } catch (e) {
      console.error('SSE parse error:', e)
    }
  })

  eventSource.onerror = (e) => {
    console.error('SSE error:', e)
    onError && onError(e)
  }

  eventSource.onclose = (e) => {
    console.log('SSE closed:', e)
    onClose && onClose(e)
  }

  return eventSource
}
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/api/index.js
git commit -m "feat(frontend): add connectSse function for SSE connection"
```

---

## Task 7: 前端 - useSse Composable

**Files:**
- Create: `frontend/src/composables/useSse.js`

- [ ] **Step 1: 创建 useSse.js**

```javascript
import { ref, onUnmounted } from 'vue'
import { connectSse } from '../api/index.js'

export function useSse(instanceId, onMessage) {
  const connected = ref(false)
  const error = ref(null)
  let eventSource = null
  let retryCount = 0
  const MAX_RETRIES = 5
  // 重试间隔: 1s, 2s, 4s, 8s, 16s
  const retryIntervals = [1000, 2000, 4000, 8000, 16000]
  let retryTimeout = null

  function connect() {
    if (!instanceId.value) return

    error.value = null

    eventSource = connectSse(
      instanceId.value,
      (data) => {
        connected.value = true
        retryCount = 0
        onMessage(data)
      },
      (e) => {
        connected.value = false
        error.value = '连接中断'
        scheduleRetry()
      },
      () => {
        connected.value = false
      }
    )
  }

  function scheduleRetry() {
    if (retryCount >= MAX_RETRIES) {
      error.value = '连接失败，请手动重连'
      return
    }

    const delay = retryIntervals[Math.min(retryCount, retryIntervals.length - 1)]
    retryCount++

    retryTimeout = setTimeout(() => {
      connect()
    }, delay)
  }

  function disconnect() {
    if (retryTimeout) {
      clearTimeout(retryTimeout)
      retryTimeout = null
    }
    if (eventSource) {
      eventSource.close()
      eventSource = null
    }
    connected.value = false
  }

  function reconnect() {
    disconnect()
    retryCount = 0
    connect()
  }

  onUnmounted(() => {
    disconnect()
  })

  return {
    connected,
    error,
    reconnect,
    disconnect,
    connect
  }
}
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/composables/useSse.js
git commit -m "feat(frontend): add useSse composable with exponential backoff retry"
```

---

## Task 8: 前端 - 4 个 Panel 组件

**Files:**
- Create: `frontend/src/components/OverviewPanel.vue`
- Create: `frontend/src/components/SessionListPanel.vue`
- Create: `frontend/src/components/AgentListPanel.vue`
- Create: `frontend/src/components/ActivityPanel.vue`

- [ ] **Step 1: 创建 OverviewPanel.vue**

```vue
<template>
  <div class="panel overview-panel">
    <div class="panel-title">实例概览</div>
    <div class="panel-content" v-if="data">
      <div class="info-row">
        <span class="label">名称</span>
        <span class="value">{{ data.instance.name }}</span>
      </div>
      <div class="info-row">
        <span class="label">URL</span>
        <span class="value url">{{ data.instance.url }}</span>
      </div>
      <div class="info-row">
        <span class="label">状态</span>
        <n-tag :type="data.instance.status === 'online' ? 'success' : 'error'" size="small">
          {{ data.instance.status === 'online' ? '在线' : '离线' }}
        </n-tag>
      </div>
      <div class="info-row">
        <span class="label">Token</span>
        <span class="value">{{ data.instance.tokenMasked }}</span>
      </div>
      <div class="info-row">
        <span class="label">最后更新</span>
        <span class="value">{{ formatTime(data.timestamp) }}</span>
      </div>
    </div>
    <div v-else class="empty">暂无数据</div>
  </div>
</template>

<script setup>
import { NTag } from 'naive-ui'

defineProps({ data: Object })

function formatTime(timestamp) {
  if (!timestamp) return '-'
  const d = new Date(timestamp)
  return d.toLocaleTimeString('zh-CN')
}
</script>

<style scoped>
.panel {
  background: #fff;
  border-radius: 8px;
  padding: 16px;
  border: 1px solid #e5e7eb;
}
.panel-title {
  font-weight: 600;
  font-size: 14px;
  margin-bottom: 12px;
  color: #1e1e2e;
}
.info-row {
  display: flex;
  justify-content: space-between;
  padding: 6px 0;
  font-size: 13px;
  border-bottom: 1px solid #f3f4f6;
}
.info-row:last-child {
  border-bottom: none;
}
.label {
  color: #6b7280;
}
.value {
  color: #1e1e2e;
  font-weight: 500;
}
.url {
  font-family: monospace;
  font-size: 12px;
}
.empty {
  text-align: center;
  color: #9ca3af;
  padding: 20px;
}
</style>
```

- [ ] **Step 2: 创建 SessionListPanel.vue**

```vue
<template>
  <div class="panel session-panel">
    <div class="panel-title">
      Session 列表
      <span class="count" v-if="data">{{ data.sessions.length }}</span>
    </div>
    <div class="panel-content" v-if="data && data.sessions.length > 0">
      <div
        v-for="session in data.sessions"
        :key="session.id"
        class="list-item"
        :class="{ abnormal: session.abnormal }"
      >
        <div class="item-main">
          <span class="item-id">{{ session.id }}</span>
          <n-tag :type="session.abnormal ? 'error' : 'success'" size="tiny">
            {{ session.status }}
          </n-tag>
        </div>
        <div class="item-sub">
          <span>Agent: {{ session.agentId }}</span>
          <span>{{ formatTime(session.createdAt) }}</span>
        </div>
      </div>
    </div>
    <div v-else-if="data" class="empty">暂无 Session</div>
    <div v-else class="empty">暂无数据</div>
  </div>
</template>

<script setup>
import { NTag } from 'naive-ui'

defineProps({ data: Object })

function formatTime(timestamp) {
  if (!timestamp) return '-'
  const d = new Date(timestamp)
  return d.toLocaleTimeString('zh-CN')
}
</script>

<style scoped>
.panel {
  background: #fff;
  border-radius: 8px;
  padding: 16px;
  border: 1px solid #e5e7eb;
  overflow: hidden;
}
.panel-title {
  font-weight: 600;
  font-size: 14px;
  margin-bottom: 12px;
  color: #1e1e2e;
  display: flex;
  align-items: center;
  gap: 8px;
}
.count {
  background: #6366f1;
  color: white;
  border-radius: 10px;
  padding: 0 6px;
  font-size: 11px;
  font-weight: normal;
}
.list-item {
  padding: 8px 10px;
  border-radius: 6px;
  margin-bottom: 6px;
  background: #f9fafb;
  border-left: 3px solid #10b981;
}
.list-item.abnormal {
  background: #fef2f2;
  border-left-color: #ef4444;
}
.item-main {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 4px;
}
.item-id {
  font-size: 12px;
  font-family: monospace;
  color: #374151;
}
.item-sub {
  display: flex;
  justify-content: space-between;
  font-size: 11px;
  color: #9ca3af;
}
.empty {
  text-align: center;
  color: #9ca3af;
  padding: 20px;
}
</style>
```

- [ ] **Step 3: 创建 AgentListPanel.vue**

```vue
<template>
  <div class="panel agent-panel">
    <div class="panel-title">
      Agent 列表
      <span class="count" v-if="data">{{ data.agents.length }}</span>
    </div>
    <div class="panel-content" v-if="data && data.agents.length > 0">
      <div
        v-for="agent in data.agents"
        :key="agent.id"
        class="list-item"
      >
        <div class="item-main">
          <span class="item-name">{{ agent.name }}</span>
          <n-tag :type="agent.status === 'active' ? 'info' : 'default'" size="tiny">
            {{ agent.status }}
          </n-tag>
        </div>
        <div class="item-sub">
          <span class="item-id">{{ agent.id }}</span>
          <span>活跃 Session: {{ agent.sessionCount }}</span>
        </div>
      </div>
    </div>
    <div v-else-if="data" class="empty">暂无 Agent</div>
    <div v-else class="empty">暂无数据</div>
  </div>
</template>

<script setup>
import { NTag } from 'naive-ui'

defineProps({ data: Object })
</script>

<style scoped>
.panel {
  background: #fff;
  border-radius: 8px;
  padding: 16px;
  border: 1px solid #e5e7eb;
  overflow: hidden;
}
.panel-title {
  font-weight: 600;
  font-size: 14px;
  margin-bottom: 12px;
  color: #1e1e2e;
  display: flex;
  align-items: center;
  gap: 8px;
}
.count {
  background: #6366f1;
  color: white;
  border-radius: 10px;
  padding: 0 6px;
  font-size: 11px;
  font-weight: normal;
}
.list-item {
  padding: 8px 10px;
  border-radius: 6px;
  margin-bottom: 6px;
  background: #f9fafb;
  border-left: 3px solid #6366f1;
}
.item-main {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 4px;
}
.item-name {
  font-size: 13px;
  font-weight: 500;
  color: #374151;
}
.item-id {
  font-size: 11px;
  font-family: monospace;
  color: #9ca3af;
}
.item-sub {
  display: flex;
  justify-content: space-between;
  font-size: 11px;
  color: #9ca3af;
}
.empty {
  text-align: center;
  color: #9ca3af;
  padding: 20px;
}
</style>
```

- [ ] **Step 4: 创建 ActivityPanel.vue**

```vue
<template>
  <div class="panel activity-panel">
    <div class="panel-title">最近活动</div>
    <div class="panel-content" v-if="data && data.activities.length > 0">
      <div
        v-for="(activity, index) in data.activities"
        :key="index"
        class="activity-item"
        :class="getActivityClass(activity.type)"
      >
        <div class="activity-icon">{{ getActivityIcon(activity.type) }}</div>
        <div class="activity-content">
          <div class="activity-detail">{{ activity.detail }}</div>
          <div class="activity-time">{{ formatTime(activity.time) }}</div>
        </div>
      </div>
    </div>
    <div v-else-if="data" class="empty">暂无活动</div>
    <div v-else class="empty">暂无数据</div>
  </div>
</template>

<script setup>
defineProps({ data: Object })

const TYPE_CONFIG = {
  session_created: { icon: '🆕', class: 'created' },
  session_ended: { icon: '👋', class: 'ended' },
  session_error: { icon: '❌', class: 'error' },
  agent_registered: { icon: '➕', class: 'registered' },
  agent_unregistered: { icon: '➖', class: 'unregistered' },
}

function getActivityIcon(type) {
  return TYPE_CONFIG[type]?.icon || '📌'
}

function getActivityClass(type) {
  return TYPE_CONFIG[type]?.class || ''
}

function formatTime(timestamp) {
  if (!timestamp) return '-'
  const d = new Date(timestamp)
  return d.toLocaleTimeString('zh-CN')
}
</script>

<style scoped>
.panel {
  background: #fff;
  border-radius: 8px;
  padding: 16px;
  border: 1px solid #e5e7eb;
  overflow: hidden;
}
.panel-title {
  font-weight: 600;
  font-size: 14px;
  margin-bottom: 12px;
  color: #1e1e2e;
}
.activity-item {
  display: flex;
  gap: 10px;
  padding: 8px 0;
  border-bottom: 1px solid #f3f4f6;
}
.activity-item:last-child {
  border-bottom: none;
}
.activity-icon {
  font-size: 14px;
  flex-shrink: 0;
}
.activity-content {
  flex: 1;
  min-width: 0;
}
.activity-detail {
  font-size: 12px;
  color: #374151;
  word-break: break-word;
}
.activity-time {
  font-size: 11px;
  color: #9ca3af;
  margin-top: 2px;
}
.activity-item.error .activity-detail {
  color: #ef4444;
}
.empty {
  text-align: center;
  color: #9ca3af;
  padding: 20px;
}
</style>
```

- [ ] **Step 5: Commit**

```bash
git add frontend/src/components/OverviewPanel.vue frontend/src/components/SessionListPanel.vue frontend/src/components/AgentListPanel.vue frontend/src/components/ActivityPanel.vue
git commit -m "feat(frontend): add 4 panel components (Overview, Session, Agent, Activity)"
```

---

## Task 9: 前端 - MonitorView 主页面

**Files:**
- Create: `frontend/src/views/MonitorView.vue`

- [ ] **Step 1: 创建 MonitorView.vue**

```vue
<template>
  <div class="monitor-view">
    <div class="sidebar">
      <div class="sidebar-title">Gateway 实例</div>
      <div v-if="instances.length === 0" class="empty">
        <n-empty description="暂无实例" size="small" />
      </div>
      <div
        v-for="inst in instances"
        :key="inst.id"
        class="instance-item"
        :class="{ selected: selectedId === inst.id }"
        @click="selectInstance(inst.id)"
      >
        <div class="instance-name">{{ inst.name }}</div>
        <div class="instance-url">{{ inst.url }}</div>
      </div>
    </div>

    <div class="main-content">
      <div v-if="!selectedId" class="no-selection">
        <n-empty description="请选择一个实例开始监控" />
      </div>

      <div v-else class="panels-grid">
        <div class="panel-cell top-left">
          <OverviewPanel :data="statusData" />
        </div>
        <div class="panel-cell top-right">
          <SessionListPanel :data="statusData" />
        </div>
        <div class="panel-cell bottom-left">
          <AgentListPanel :data="statusData" />
        </div>
        <div class="panel-cell bottom-right">
          <ActivityPanel :data="statusData" />
        </div>
      </div>

      <!-- 连接状态提示 -->
      <div v-if="error" class="connection-error">
        <span>{{ error }}</span>
        <n-button size="tiny" @click="handleReconnect">重连</n-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, watch, computed } from 'vue'
import { NEmpty, NButton } from 'naive-ui'
import OverviewPanel from '../components/OverviewPanel.vue'
import SessionListPanel from '../components/SessionListPanel.vue'
import AgentListPanel from '../components/AgentListPanel.vue'
import ActivityPanel from '../components/ActivityPanel.vue'
import { useSse } from '../composables/useSse.js'

const props = defineProps({
  instances: {
    type: Array,
    default: () => []
  }
})

const selectedId = ref(null)
const statusData = ref(null)
const error = ref(null)

const selectedIdRef = computed(() => selectedId.value)

const { connected, reconnect } = useSse(selectedIdRef, (data) => {
  statusData.value = data
  error.value = null
})

function selectInstance(id) {
  if (selectedId.value === id) return
  selectedId.value = id
  statusData.value = null
}

function handleReconnect() {
  error.value = null
  reconnect()
}
</script>

<style scoped>
.monitor-view {
  display: flex;
  height: calc(100vh - 120px);
  background: #f3f4f6;
  gap: 16px;
  padding: 16px;
}

.sidebar {
  width: 240px;
  background: #fff;
  border-radius: 8px;
  padding: 16px;
  border: 1px solid #e5e7eb;
  overflow-y: auto;
  flex-shrink: 0;
}

.sidebar-title {
  font-weight: 600;
  font-size: 14px;
  margin-bottom: 12px;
  color: #1e1e2e;
}

.instance-item {
  padding: 10px 12px;
  border-radius: 6px;
  cursor: pointer;
  margin-bottom: 6px;
  border: 2px solid transparent;
  transition: all 0.2s;
}

.instance-item:hover {
  background: #f9fafb;
}

.instance-item.selected {
  background: #eef2ff;
  border-color: #6366f1;
}

.instance-name {
  font-size: 13px;
  font-weight: 500;
  color: #374151;
  margin-bottom: 2px;
}

.instance-url {
  font-size: 11px;
  color: #9ca3af;
  font-family: monospace;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.main-content {
  flex: 1;
  position: relative;
  min-width: 0;
}

.no-selection {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #fff;
  border-radius: 8px;
  border: 1px solid #e5e7eb;
}

.panels-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  grid-template-rows: 1fr 1fr;
  gap: 16px;
  height: 100%;
}

.panel-cell {
  min-height: 0;
  overflow: hidden;
}

.panel-cell > * {
  height: 100%;
}

.connection-error {
  position: absolute;
  bottom: 24px;
  left: 50%;
  transform: translateX(-50%);
  background: #fef2f2;
  border: 1px solid #fca5a5;
  border-radius: 8px;
  padding: 10px 16px;
  display: flex;
  align-items: center;
  gap: 12px;
  color: #dc2626;
  font-size: 13px;
}

.empty {
  padding: 20px 0;
}
</style>
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/views/MonitorView.vue
git commit -m "feat(frontend): add MonitorView with 4-panel layout and SSE integration"
```

---

## Task 10: 前端 - 添加 Monitor 标签页

**Files:**
- Modify: `frontend/src/App.vue`

- [ ] **Step 1: 修改 App.vue**

在 import 语句中添加 MonitorView:

```javascript
import Dashboard from './views/Dashboard.vue'
import InstanceManager from './views/InstanceManager.vue'
import MonitorView from './views/MonitorView.vue'
```

在 tabs 中添加 Monitor 标签页:

```vue
<n-tabs type="line" animated v-model:value="activeTab">
  <n-tab-pane name="dashboard" tab="📊 Dashboard">
    <Dashboard :instances="instances" @refresh="loadInstances" />
  </n-tab-pane>
  <n-tab-pane name="monitor" tab="📡 实时监控">
    <MonitorView :instances="instances" />
  </n-tab-pane>
  <n-tab-pane name="instances" tab="🖥️ 实例管理">
    <InstanceManager :instances="instances" @refresh="loadInstances" />
  </n-tab-pane>
</n-tabs>
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/App.vue
git commit -m "feat(frontend): add Monitor tab to main app navigation"
```

---

## Task 11: 验证和测试

- [ ] **Step 1: 后端启动测试**

```bash
cd backend
./mvnw spring-boot:run
# 预期：启动成功，无报错
```

- [ ] **Step 2: 前端启动测试**

```bash
cd frontend
npm run dev
# 预期：启动成功，运行在 http://localhost:5173
```

- [ ] **Step 3: 功能验证**

1. 打开 http://localhost:5173
2. 添加一个 Gateway 实例
3. 切换到"实时监控"标签页
4. 选择实例，观察 4 面板是否显示数据
5. 等待 15 秒，确认数据有刷新
6. 检查连接状态提示

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "feat: complete real-time agent session monitoring feature"
```

---

## 实现顺序总结

1. **Task 1**: DTO 创建（StatusData, ActivityEvent）
2. **Task 2**: SseEmitterService（SSE 连接管理）
3. **Task 3**: MonitorService（定时轮询 + 状态缓存）
4. **Task 4**: SseStatusController（SSE 端点）
5. **Task 5**: 启用 @EnableScheduling
6. **Task 6**: 前端 API（connectSse）
7. **Task 7**: useSse composable（重连逻辑）
8. **Task 8**: 4 个 Panel 组件
9. **Task 9**: MonitorView 主页面
10. **Task 10**: App.vue 添加标签页
11. **Task 11**: 验证和测试
