# OpenClaw Orchestrator

OpenClaw 多 Agent 编排与监控系统。

## 技术栈

- **后端**: Spring Boot 3 + Java 17 + MySQL
- **前端**: Vue 3 + Naive UI + Vite
- **构建**: Maven

## 快速开始

### 1. 初始化数据库

```sql
CREATE DATABASE openclaw_orchestrator;
```

### 2. 修改数据库配置

编辑 `backend/src/main/resources/application.yml`：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/openclaw_orchestrator?useSSL=false&serverTimezone=Asia/Shanghai
    username: your_username
    password: your_password
```

### 3. 启动后端

```bash
cd backend
./mvnw spring-boot:run
# 后端运行在 http://localhost:8080
```

### 4. 启动前端

```bash
cd frontend
npm install
npm run dev
# 前端运行在 http://localhost:5173
```

## 功能

- 多 OpenClaw Gateway 实例管理
- 实时 Agent 状态监控
- Session 列表查看
- 子 Agent 监控
- 记忆搜索
