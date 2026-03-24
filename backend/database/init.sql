-- 创建数据库（运行前确保 MySQL 服务已启动）
CREATE DATABASE IF NOT EXISTS openclaw_orchestrator
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE openclaw_orchestrator;

-- Gateway 实例表（Spring Boot JPA 会自动创建，这里写出来方便参考）
-- CREATE TABLE gateway_instances (
--     id          BIGINT AUTO_INCREMENT PRIMARY KEY,
--     name        VARCHAR(255) NOT NULL,
--     url         VARCHAR(512) NOT NULL,
--     token       TEXT NOT NULL,
--     description TEXT,
--     created_at  DATETIME DEFAULT CURRENT_TIMESTAMP
--) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 初始化一条示例数据（指向本机 WSL Gateway）
-- INSERT INTO gateway_instances (name, url, token, description)
-- VALUES (
--     'Jack (WSL)',
--     'http://127.0.0.1:18789',
--     'YOUR_TOKEN_HERE',
--     '本地 WSL2 上的 OpenClaw Gateway'
-- );
