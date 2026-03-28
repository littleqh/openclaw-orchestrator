# 令牌认证与用户管理设计

## 1. 背景与目标

本系统（OpenClaw Orchestrator）目前所有 API 均无认证保护。需要新增两套独立的认证机制：

1. **用户认证**：保护前端页面和后端 API，人类用户注册登录后访问
2. **Agent 令牌认证**：OpenClaw 远程调用本系统接口时携带的 Auth 令牌

## 2. 认证架构

### 2.1 双轨认证设计

| | 用户认证 | Agent 令牌认证 |
|---|---|---|
| 前缀 | `User <jwt>` | `Agent <uuid>` |
| 认证对象 | 人类用户 | OpenClaw Agent |
| 存储 | User 表 + JWT 签名 | AgentToken 表（UUID 明文存储） |
| 验证方式 | JWT 签名验证 | 数据库查询验证 |
| 用途 | 保护前端所有页面/API | OpenClaw 调用本系统 API |

### 2.2 前缀分流机制

`AuthFilter` 根据 `Authorization` Header 前缀判断认证类型：
- `User ` 开头 → 走用户 JWT 验证
- `Agent ` 开头 → 走 Agent UUID 验证
- 无 Authorization → 放行登录/注册接口，其他返回 401

## 3. 数据模型

### 3.1 User 表（新增）

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 主键 |
| username | VARCHAR(50) | UNIQUE, NOT NULL | 用户名 |
| password | VARCHAR(255) | NOT NULL | BCrypt 加密密码 |
| role | VARCHAR(20) | NOT NULL | 固定 `ADMIN` |
| created_at | DATETIME | NOT NULL | 创建时间 |

### 3.2 AgentToken 表（新增）

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 主键 |
| token | VARCHAR(36) | UNIQUE, NOT NULL | UUID 令牌 |
| worker_id | BIGINT | NULLABLE, FK(workers.id) | 关联 Worker，null = 系统级令牌 |
| created_at | DATETIME | NOT NULL | 创建时间 |
| last_access_at | DATETIME | | 最后访问时间（Filter 自动更新） |

**约束**：
- 系统级令牌：只能有一条 `worker_id = NULL` 的记录（应用层保证）
- Agent 级令牌：每个 Worker 最多一条令牌（应用层保证）

### 3.3 TokenAccessLog 表（新增）

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 主键 |
| token_id | BIGINT | FK(agent_tokens.id) | 关联 AgentToken |
| access_path | VARCHAR(255) | NOT NULL | 访问的 API 路径 |
| access_ip | VARCHAR(45) | NOT NULL | 客户端 IP |
| access_time | DATETIME | NOT NULL | 访问时间 |
| success | BOOLEAN | NOT NULL | 认证是否成功 |

## 4. 后端组件

### 4.1 过滤器链

```
请求 ──▶ AuthFilter（一级入口）
              │
          提取前缀
         /       \
    User JWT    Agent UUID
        │           │
    JwtAuth     AgentToken
    Filter      Filter
        │           │
    验证签名      查询DB
        │           │
    成功→用户身份  成功→Agent身份
        │           │
        └─────┬─────┘
              ▼
        继续处理请求
```

#### AuthFilter（统一入口）

- 拦截所有 `/api/**`
- 从 Header 提取 `Authorization` 值
- 根据前缀分流：
  - `User ` → 设置用户身份，继续
  - `Agent ` → 设置 Agent 身份，继续
  - 无/其他 → 判断路径是否公开（`/api/auth/**`），是则放行，否则返回 401

#### JwtAuthFilter（用户 JWT 验证）

- 提取 `User <jwt>` 前缀后的 token
- 调用 `JwtService` 验证签名并解析
- 验证失败返回 401
- 成功则设置 Security Context

#### AgentTokenFilter（Agent UUID 验证）

- 提取 `Agent <uuid>` 前缀后的 token
- 查询 `AgentTokenRepository` 验证存在性
- 无论成功/失败都记录 `TokenAccessLog`
- 成功时更新 `last_access_at`
- 验证失败返回 401

### 4.2 Service 层

#### JwtService

- `generateToken(User user)` — 生成 JWT，包含 userId, username, role
- `validateToken(String token)` — 验证 JWT 签名和有效期
- `getUserFromToken(String token)` — 从 JWT 解析用户信息

#### AuthService

- `register(username, password)` — 注册用户
  - 首个用户 role = `ADMIN`，后续用户 role = `ADMIN`
  - 密码 BCrypt 加密
  - 用户名已存在返回 409
- `login(username, password)` — 登录
  - 验证用户名密码
  - 调用 `JwtService.generateToken()` 生成 JWT
  - 返回 JWT

#### AgentTokenService

- `createSystemToken()` — 创建系统级令牌（worker_id = null）
  - 已存在则抛出异常
  - 返回生成的 UUID
- `createAgentToken(workerId)` — 为 Worker 创建令牌
  - Worker 不存在则抛出异常
  - 该 Worker 已有令牌则抛出异常
  - 返回生成的 UUID
- `resetToken(id)` — 重置令牌
  - 删除旧令牌，生成新 UUID
  - 返回新令牌
- `deleteToken(id)` — 删除令牌
- `getTokenInfo(id)` — 获取令牌元数据（不暴露完整 token，只显示后4位）
- `getAllTokens()` — 获取所有令牌列表（含关联的 Worker 名称）
- `validateToken(String token)` — 验证令牌，返回 AgentToken 或 null

### 4.3 Controller 层

#### AuthController (`/api/auth/**`)

| 方法 | 路径 | 说明 | 认证 |
|------|------|------|------|
| POST | /register | 注册 | 否 |
| POST | /login | 登录 | 否 |

**请求/响应示例**：
```json
// POST /api/auth/register
// Request: { "username": "admin", "password": "123456" }
// Response: { "id": 1, "username": "admin", "role": "ADMIN" }

// POST /api/auth/login
// Request: { "username": "admin", "password": "123456" }
// Response: { "token": "eyJhbGciOiJIUzI1NiJ9..." }
```

#### AgentTokenController (`/api/tokens/**`)

| 方法 | 路径 | 说明 | 认证 |
|------|------|------|------|
| GET | / | 获取所有令牌 | User |
| POST | / | 创建令牌 | User |
| GET | /{id} | 获取令牌详情 | User |
| PUT | /{id}/reset | 重置令牌 | User |
| DELETE | /{id} | 删除令牌 | User |

**请求/响应示例**：
```json
// POST /api/tokens
// Request: { } // 空body = 系统令牌
// Request: { "workerId": 1 } // Agent令牌
// Response: { "id": 1, "token": "550e8400-e29b-41d4-a716-446655440000", "type": "SYSTEM", "createdAt": "..." }

// GET /api/tokens/1
// Response: { "id": 1, "tokenPreview": "****-****-****-a716", "type": "SYSTEM", "workerId": null, "workerName": null, "createdAt": "...", "lastAccessAt": "..." }

// PUT /api/tokens/1/reset
// Response: { "id": 1, "token": "new-uuid-here", "type": "SYSTEM", "createdAt": "..." }
```

## 5. 前端组件

### 5.1 路由

| 路径 | 组件 | 说明 | 认证要求 |
|------|------|------|----------|
| /login | LoginView | 登录/注册页 | 否 |
| /token-management | TokenManagement | 令牌管理 | 是 |

**路由守卫**：未登录用户访问需要认证的页面时，跳转 `/login`

### 5.2 页面

#### LoginView

- Tab 切换：登录 / 注册
- 登录表单：用户名 + 密码
- 注册表单：用户名 + 密码 + 确认密码
- 登录成功：存储 JWT 到 localStorage，跳转 Dashboard

#### TokenManagement

- NTabs 组件：系统令牌 | Agent 令牌

**系统令牌 Tab**：
- 首次：显示「创建系统令牌」按钮
- 创建后：显示令牌卡片（UUID 后4位预览）、复制按钮、重置按钮

**Agent 令牌 Tab**：
- Worker 列表，每行显示：
  - Worker 名称
  - 令牌状态（有/无）
  - 有令牌：预览后4位 + 复制 + 重置 + 删除
  - 无令牌：创建按钮
- 创建时选择 Worker（下拉选择）

### 5.3 API 模块

#### authApi.js

```javascript
export const authApi = {
  login: (username, password) => post('/api/auth/login', { username, password }),
  register: (username, password) => post('/api/auth/register', { username, password }),
}
```

#### tokenApi.js

```javascript
export const tokenApi = {
  getAll: () => get('/api/tokens'),
  create: (workerId?) => post('/api/tokens', workerId ? { workerId } : {}),
  getById: (id) => get(`/api/tokens/${id}`),
  reset: (id) => put(`/api/tokens/${id}/reset`),
  delete: (id) => delete(`/api/tokens/${id}`),
}
```

### 5.4 Axios 拦截器

```javascript
// 请求拦截器：添加 Authorization Header
axios.interceptors.request.use(config => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `User ${token}`
  }
  return config
})

// 响应拦截器：401 => 跳转登录页
axios.interceptors.response.use(
  response => response,
  error => {
    if (error.response?.status === 401) {
      localStorage.removeItem('token')
      router.push('/login')
    }
    return Promise.reject(error)
  }
)
```

## 6. 安全考虑

1. **密码存储**：BCrypt 加密，不可逆
2. **JWT 安全**：
   - 签名算法：HS256
   - 有效期：可配置（默认 24h）
   - 密钥：从环境变量或配置文件读取
3. **Agent Token 存储**：UUID 明文存储（机器凭证，无需加密）
4. **日志审计**：所有 Agent Token 访问都记录日志
5. **公开路径**：`/api/auth/**`（登录注册）无需认证

## 7. 错误处理

| 场景 | HTTP 状态码 | 错误信息 |
|------|-------------|----------|
| 用户名已存在 | 409 | "Username already exists" |
| 用户名/密码错误 | 401 | "Invalid credentials" |
| 系统令牌已存在 | 409 | "System token already exists" |
| Worker 令牌已存在 | 409 | "Token for this worker already exists" |
| Worker 不存在 | 404 | "Worker not found" |
| 令牌不存在 | 404 | "Token not found" |
| Agent 认证失败 | 401 | "Invalid token" |
| JWT 过期/无效 | 401 | "Invalid or expired token" |
| 未登录访问受保护资源 | 401 | "Unauthorized" |

## 8. 实现顺序

1. **User 实体 + Repository + 基础 CRUD**
2. **JWT Service + JwtAuthFilter**
3. **AuthController（登录/注册）**
4. **AuthFilter（统一入口，分流）**
5. **AgentToken 实体 + Repository**
6. **AgentTokenService**
7. **AgentTokenFilter + TokenAccessLog**
8. **AgentTokenController**
9. **前端：LoginView + 路由守卫**
10. **前端：TokenManagement 页面**
11. **集成测试**

## 9. 文件清单

### Backend 新增
- `entity/User.java`
- `entity/AgentToken.java`
- `entity/TokenAccessLog.java`
- `repository/UserRepository.java`
- `repository/AgentTokenRepository.java`
- `repository/TokenAccessLogRepository.java`
- `service/JwtService.java`
- `service/AuthService.java`
- `service/AgentTokenService.java`
- `filter/AuthFilter.java`
- `filter/JwtAuthFilter.java`
- `filter/AgentTokenFilter.java`
- `controller/AuthController.java`
- `controller/AgentTokenController.java`
- `dto/LoginRequest.java`
- `dto/RegisterRequest.java`
- `dto/TokenCreateRequest.java`
- `dto/TokenResponse.java`

### Backend 修改
- `application.properties`（JWT 密钥配置）
- `config/WebConfig.java`（注册 Filter）

### Frontend 新增
- `views/LoginView.vue`
- `views/TokenManagement.vue`
- `api/authApi.js`
- `api/tokenApi.js`
- `router/index.js`（新增路由 + 守卫）

### Frontend 修改
- `api/index.js`（添加 axios 拦截器）
