-- 工作流相关表

USE openclaw_orchestrator;

-- 1. 输出格式表（预定义格式）
CREATE TABLE IF NOT EXISTS output_schemas (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    json_schema TEXT NOT NULL COMMENT 'JSON Schema 定义输出格式',
    created_at  DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 2. 工作流模板表
CREATE TABLE IF NOT EXISTS business_workflows (
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    name                 VARCHAR(200) NOT NULL,
    description          TEXT COMMENT '长文本描述',
    default_max_retries  INT DEFAULT 3 COMMENT '默认最大重试次数',
    is_active            BOOLEAN DEFAULT TRUE,
    is_system            BOOLEAN DEFAULT FALSE COMMENT '是否系统内置模板',
    created_by          BIGINT,
    created_at          DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 3. 工作流环节表
CREATE TABLE IF NOT EXISTS workflow_stages (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    workflow_id     BIGINT NOT NULL,
    name            VARCHAR(200) NOT NULL,
    description     TEXT COMMENT '长文本环节描述',
    stage_order     INT NOT NULL COMMENT '环节顺序',
    task_type       VARCHAR(20) NOT NULL DEFAULT 'AUTO' COMMENT 'AUTO=自动, APPROVAL=审批',
    worker_id       BIGINT COMMENT '负责的数字员工ID (可为null)',
    approver_id     BIGINT COMMENT '审批人ID (task_type=APPROVAL时必填)',
    output_schema_id BIGINT COMMENT '输出格式ID',
    max_retries     INT COMMENT '覆盖工作流默认重试次数',
    priority        INT DEFAULT 0 COMMENT '优先级，用于排序',
    next_stage_id   BIGINT COMMENT '正常情况下的下一个环节',
    condition_expr  VARCHAR(500) COMMENT '条件表达式，如: output.status == true',
    x               INT COMMENT '节点 x 坐标',
    y               INT COMMENT '节点 y 坐标',
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (workflow_id) REFERENCES business_workflows(id) ON DELETE CASCADE,
    FOREIGN KEY (worker_id) REFERENCES workers(id) ON DELETE SET NULL,
    FOREIGN KEY (approver_id) REFERENCES users(id) ON DELETE SET NULL,
    FOREIGN KEY (output_schema_id) REFERENCES output_schemas(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 4. 条件分支表（一个环节可能有多个分支）
CREATE TABLE IF NOT EXISTS workflow_stage_branches (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    stage_id        BIGINT NOT NULL COMMENT '所属环节',
    target_stage_id BIGINT NOT NULL COMMENT '目标环节ID',
    condition_expr  VARCHAR(500) NOT NULL COMMENT '条件表达式',
    branch_order    INT DEFAULT 0 COMMENT '分支顺序',
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (stage_id) REFERENCES workflow_stages(id) ON DELETE CASCADE,
    FOREIGN KEY (target_stage_id) REFERENCES workflow_stages(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 5. 工作流实例表（一次具体的工作流执行）
CREATE TABLE IF NOT EXISTS workflow_instances (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    workflow_id     BIGINT NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'CREATE' COMMENT 'CREATE=创建, PLANNED=已指派, READY=就绪, RUNNING=执行中, PENDING=等待, PROCESSING=处理中, PAUSED=暂停, COMPLETED=完成, FAILED=失败, TERMINATED=终止',
    description     TEXT COMMENT '用户输入的自然语言任务描述',
    current_stage_id BIGINT COMMENT '当前环节ID',
    variables       TEXT COMMENT '全局上下文变量，JSON格式',
    started_by      BIGINT COMMENT '启动人',
    started_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    completed_at    DATETIME,
    updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (workflow_id) REFERENCES business_workflows(id),
    FOREIGN KEY (current_stage_id) REFERENCES workflow_stages(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 6. 任务表
CREATE TABLE IF NOT EXISTS tasks (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    instance_id     BIGINT NOT NULL,
    stage_id        BIGINT NOT NULL,
    worker_id       BIGINT COMMENT '任务负责人',
    type            VARCHAR(20) NOT NULL DEFAULT 'AUTO' COMMENT 'AUTO=自动, APPROVAL=审批',
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING, PROCESSING, COMPLETED, FAILED, PAUSED, APPROVED, REJECTED',
    priority        INT DEFAULT 0,
    retry_count     INT DEFAULT 0,
    max_retries     INT DEFAULT 3,
    output          TEXT COMMENT '任务输出，JSON格式',
    approver_id     BIGINT COMMENT '审批人ID',
    approver_comments TEXT COMMENT '审批意见',
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (instance_id) REFERENCES workflow_instances(id) ON DELETE CASCADE,
    FOREIGN KEY (stage_id) REFERENCES workflow_stages(id),
    FOREIGN KEY (worker_id) REFERENCES workers(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 7. 任务执行日志表
CREATE TABLE IF NOT EXISTS task_execution_logs (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id         BIGINT NOT NULL,
    action          VARCHAR(50) NOT NULL COMMENT 'CREATE, START, COMPLETE, FAIL, PAUSE, RESUME, APPROVE, REJECT, ADMIN_FORCE',
    old_status      VARCHAR(20),
    new_status      VARCHAR(20),
    operator        VARCHAR(100) COMMENT '操作人 (Worker ID 或 Admin ID)',
    comment         TEXT,
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (task_id) REFERENCES tasks(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 8. 工作流实例日志表
CREATE TABLE IF NOT EXISTS workflow_instance_logs (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    instance_id     BIGINT NOT NULL,
    action          VARCHAR(50) NOT NULL COMMENT 'START, PAUSE, RESUME, COMPLETE, FAIL, TERMINATE',
    old_status      VARCHAR(20),
    new_status      VARCHAR(20),
    operator        VARCHAR(100) COMMENT '操作人',
    comment         TEXT,
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (instance_id) REFERENCES workflow_instances(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 9. 通知表
CREATE TABLE IF NOT EXISTS notifications (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    type            VARCHAR(50) NOT NULL COMMENT 'APPROVAL_REQUIRED, TASK_TIMEOUT, WORKFLOW_COMPLETED, etc.',
    title           VARCHAR(200) NOT NULL,
    message         TEXT,
    task_id         BIGINT,
    instance_id     BIGINT,
    recipient_id    BIGINT NOT NULL COMMENT '通知接收人',
    is_read         BOOLEAN DEFAULT FALSE,
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (task_id) REFERENCES tasks(id) ON DELETE SET NULL,
    FOREIGN KEY (instance_id) REFERENCES workflow_instances(id) ON DELETE SET NULL,
    FOREIGN KEY (recipient_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 10. 任务文件表（任务产生的文件）
CREATE TABLE IF NOT EXISTS task_artifacts (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id         BIGINT NOT NULL,
    file_name       VARCHAR(255) NOT NULL,
    file_path       VARCHAR(500) NOT NULL,
    file_type       VARCHAR(50) COMMENT 'result, log, attachment',
    file_size       BIGINT,
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (task_id) REFERENCES tasks(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 插入预定义的输出格式
INSERT INTO output_schemas (name, description, json_schema) VALUES
('纯文本', '简单的文本输出', '{"type": "object", "properties": {"text": {"type": "string", "description": "文本内容"}}, "required": ["text"]}'),
('JSON数据', '结构化JSON数据', '{"type": "object", "properties": {"data": {"type": "object", "description": "结构化数据"}}, "required": ["data"]}'),
('键值对', '键值对形式', '{"type": "object", "additionalProperties": {"type": "string"}, "description": "键值对数据"}'),
('文件列表', '文件输出列表', '{"type": "object", "properties": {"files": {"type": "array", "items": {"type": "object", "properties": {"name": {"type": "string"}, "path": {"type": "string"}}}}}}'),
('空输出', '不需要输出', '{"type": "object", "properties": {}}');

-- 插入系统任务模板
INSERT INTO business_workflows (name, description, is_system, default_max_retries, is_active) VALUES
('提示词生成', '系统内置：生成并校验提示词', TRUE, 3, TRUE);

-- 提示词生成模板的环节
INSERT INTO workflow_stages (workflow_id, name, description, stage_order, task_type) VALUES
(1, '生成提示词', '根据用户需求生成提示词内容', 1, 'AUTO'),
(1, '校验提示词', '校验生成的提示词是否合理', 2, 'AUTO');

-- 任务分配模板
INSERT INTO business_workflows (name, description, is_system, default_max_retries, is_active) VALUES
('任务分配', '系统内置：分析任务描述并分配执行员工', TRUE, 3, TRUE);

-- 任务分配模板的环节
INSERT INTO workflow_stages (workflow_id, name, description, stage_order, task_type) VALUES
(2, '任务分配', '分析用户描述的任务，自动分配合适的执行员工', 1, 'AUTO');

-- 为已存在的表添加 x, y 坐标列
ALTER TABLE workflow_stages ADD COLUMN IF NOT EXISTS x INT COMMENT '节点 x 坐标' AFTER condition_expr;
ALTER TABLE workflow_stages ADD COLUMN IF NOT EXISTS y INT COMMENT '节点 y 坐标' AFTER x;
