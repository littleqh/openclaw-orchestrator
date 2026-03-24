# 实时 Agent/Session 监控系统设计

## 1. 背景与目标

**问题：** 当前系统只能手动刷新查看 Gateway 实例状态，无法实时监控 Agent 和 Session 的运行状态。

**目标：** 实现实时 Agent/Session 监控，包含：
- 左侧实例列表，支持选择不同 Gateway 实例
- 右侧多面板详情，同时展示 4 类信息
- 实时数据推送（SSE），15 秒轮询间隔
- 异常项高亮标记

---

## 2. 整体架构

```
┌─────────────┐     ┌──────────────────┐     ┌─────────────────┐
│   Browser   │ ←──→ │   Spring Boot    │ ←──→ │ OpenClaw Gateway│
│   (Vue)     │ SSE │   (Backend)      │ REST│   (REST API)   │
└─────────────┘     └──────────────────┘     └─────────────────┘
```

**工作流程：**
1. 后端定时轮询 Gateway（每 15 秒）
2. Gateway 返回 Session/Agent 状态
3. 后端通过 SSE 推送到前端
4. 前端更新 4 个详情面板
5. 异常项（Session error/failed）红色高亮标记

---

## 3. 前端设计

### 3.1 页面布局

```
┌──────────────────────────────────────────────────────┐
│                    顶部导航/标题                        │
├────────────┬─────────────────────────────────────────┤
│            │  ┌─────────────┐ ┌─────────────────┐    │
│  实例列表   │  │  实例概览   │ │   Session列表   │    │
│            │  └─────────────┘ └─────────────────┘    │
│ (点击选择)  │  ┌─────────────┐ ┌─────────────────┐    │
│            │  │  Agent列表  │ │   最近活动      │    │
│            │  └─────────────┘ └─────────────────┘    │
└────────────┴─────────────────────────────────────────┘
```

### 3.2 左侧面板 - 实例列表

- 展示所有已注册的 Gateway 实例
- 显示：实例名称、URL、在线/离线状态指示
- 点击选中，右侧展示该实例详情
- 支持添加/删除实例（已有功能）

### 3.3 右侧面板 - 4 面板布局

| 面板 | 内容 |
|-----|------|
| **实例概览** | 实例名、URL、连接状态、最后更新时间、Token 脱敏显示 |
| **Session 列表** | Session ID、状态（正常/异常）、创建时间、所属 Agent |
| **Agent 列表** | Agent ID、名称、状态、活跃 Session 数 |
| **最近活动** | 时间线展示最近 20 条状态变化事件 |

### 3.4 异常检测规则

- Session 状态为 `error` / `failed` → 异常（红色高亮）
- 连接失败 → 实例离线标记（灰色/红色指示）

---

## 4. 后端设计

### 4.1 新增 API

| 端点 | 方法 | 功能 |
|-----|------|------|
| `/api/sse/status/{instanceId}` | GET | SSE 流，推送实时监控数据 |
| `/api/instances/{id}/status` | GET | 获取实例当前状态（快照） |

### 4.2 SSE 数据格式

```json
{
  "type": "status_update",
  "timestamp": "2026-03-24T20:00:00Z",
  "instanceId": 1,
  "instance": {
    "id": 1,
    "name": "Gateway-1",
    "url": "http://localhost:8081",
    "status": "online",
    "lastUpdate": "2026-03-24T20:00:00Z"
  },
  "sessions": [
    {"id": "sess_xxx", "status": "active", "agentId": "agent:main", "createdAt": "..."}
  ],
  "agents": [
    {"id": "agent:main", "name": "Main Agent", "status": "active", "sessionCount": 3}
  ],
  "activities": [
    {"time": "...", "type": "session_created", "detail": "..."}
  ]
}
```

### 4.3 定时任务

- 每 15 秒轮询指定实例的 Gateway
- 调用 `sessions_list` 和 `subagents` 工具
- 将结果缓存并通过 SSE 推送

### 4.4 服务端组件

```
├── controller/
│   └── SseStatusController.java    # SSE 端点
├── service/
│   ├── GatewayService.java         # 已有，扩展状态轮询
│   └── SseEmitterService.java      # SSE 连接管理
├── dto/
│   └── StatusData.java             # 状态数据 DTO
└── config/
    └── SseConfig.java              # SSE 配置
```

---

## 5. 数据流

1. **前端连接 SSE**：`GET /api/sse/status/{instanceId}`
2. **后端立即推送**：从缓存获取最新状态并推送
3. **后端定时轮询**：每 15 秒调用 Gateway API
4. **状态变化时推送**：只有数据变化时才推送，减少流量
5. **异常标记**：Session 状态为 error/failed 时标记

---

## 6. 实现步骤

### Phase 1: 基础架构
1. 创建 SSE 端点 `SseStatusController`
2. 创建 `SseEmitterService` 管理连接
3. 创建 `StatusData` DTO
4. 实现 15 秒定时轮询

### Phase 2: 前端页面
1. 修改 `Dashboard.vue` 或新建 `MonitorView.vue`
2. 左侧实例列表（复用已有组件）
3. 右侧 4 面板布局
4. SSE 客户端连接逻辑
5. 异常高亮样式

### Phase 3: 调试优化
1. 连接稳定性测试
2. 异常情况处理（Gateway 不可用）
3. 性能优化

---

## 7. 技术选型

- **SSE**：Spring `SseEmitter`，浏览器原生支持，无需额外库
- **轮询**：Spring `@Scheduled`
- **前端状态**：Vue 3 `ref`/`reactive`，Naive UI 组件
- **连接管理**：后端维护 Map<instanceId, List<SseEmitter>>

---

## 8. 风险与限制

1. **Gateway 性能**：15 秒轮询对 Gateway 压力可控
2. **多实例并发**：需管理多个 SSE 连接，后端已考虑
3. **断线重连**：前端需处理 SSE 断开，自动重连逻辑
4. **历史数据**：最近活动仅保留内存中，服务重启丢失
