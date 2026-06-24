# 数据库迁移指南

本文档说明数据库 schema 变更的管理和迁移流程。

## 📋 目录结构

```
docs/database/
├── init.sql                    # 完整初始化脚本
├── schema.md                   # 数据库设计文档
└── migrations/                 # 迁移脚本目录 (新建)
    ├── V1.0.0__init_schema.sql
    ├── V1.1.0__add_customer_index.sql
    ├── V1.2.0__add_work_order_fields.sql
    └── README.md
```

---

## 🔄 迁移策略

### 方案选择

#### 方案 1: Flyway (推荐)

**优点**:
- ✅ 自动化版本管理
- ✅ 支持回滚
- ✅ 与 Spring Boot 集成良好
- ✅ 社区活跃

**配置**:
```xml
<!-- pom.xml -->
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
</dependency>
```

```yaml
# application.yml
spring:
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true
    validate-on-migrate: true
```

#### 方案 2: Liquibase

**优点**:
- ✅ 支持多种格式 (XML, YAML, JSON, SQL)
- ✅ 更细粒度的控制
- ✅ 支持回滚脚本

#### 方案 3: 手动 SQL 脚本 (当前方案)

**优点**:
- ✅ 简单直接
- ✅ 无额外依赖
- ✅ 完全控制

**缺点**:
- ❌ 需要手动管理版本
- ❌ 容易遗漏执行

---

## 📝 迁移脚本规范

### 命名规范

```
V{version}__{description}.sql

示例:
V1.0.0__init_schema.sql
V1.1.0__add_customer_phone_index.sql
V1.2.0__alter_work_order_add_priority.sql
```

**规则**:
- 版本号使用三位数字 (主版本.次版本.修订版本)
- 描述部分使用下划线分隔的小写字母
- 双下划线 `__` 分隔版本号和描述

### 脚本模板

```sql
-- ============================================
-- Migration: V1.1.0__add_customer_phone_index.sql
-- Description: 为客户表手机号字段添加索引
-- Author: huangrenhui
-- Date: 2024-01-15
-- ============================================

-- 开始事务
START TRANSACTION;

-- 添加索引
CREATE INDEX idx_customer_phone ON cs_customer(phone);

-- 验证索引是否创建成功
SELECT COUNT(*) INTO @index_count 
FROM information_schema.statistics 
WHERE table_name = 'cs_customer' 
  AND index_name = 'idx_customer_phone';

-- 如果索引创建失败，回滚事务
SET @msg = IF(@index_count > 0, 
              'Index created successfully', 
              'Failed to create index');
SELECT @msg;

-- 提交事务
COMMIT;
```

---

## 🗄️ 当前数据库 Schema

### 核心表结构

#### 1. cs_customer (客户表)

```sql
CREATE TABLE cs_customer (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    customer_no VARCHAR(32) NOT NULL COMMENT '客户编号',
    name VARCHAR(64) NOT NULL COMMENT '客户姓名',
    phone VARCHAR(20) COMMENT '手机号',
    email VARCHAR(128) COMMENT '邮箱',
    company VARCHAR(128) COMMENT '公司名称',
    remark TEXT COMMENT '备注',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除: 0-未删除, 1-已删除',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    
    UNIQUE KEY uk_customer_no (customer_no),
    INDEX idx_phone (phone),
    INDEX idx_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客户表';
```

#### 2. cs_agent (坐席表)

```sql
CREATE TABLE cs_agent (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    agent_no VARCHAR(32) NOT NULL COMMENT '坐席编号',
    username VARCHAR(32) NOT NULL COMMENT '用户名',
    password VARCHAR(128) NOT NULL COMMENT '密码(BCrypt加密)',
    nickname VARCHAR(64) COMMENT '昵称',
    avatar VARCHAR(256) COMMENT '头像URL',
    status TINYINT DEFAULT 1 COMMENT '状态: 0-离线, 1-在线, 2-忙碌',
    max_concurrent INT DEFAULT 5 COMMENT '最大并发会话数',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    
    UNIQUE KEY uk_agent_no (agent_no),
    UNIQUE KEY uk_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='坐席表';
```

#### 3. cs_chat_session (会话表)

```sql
CREATE TABLE cs_chat_session (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    session_id VARCHAR(64) NOT NULL COMMENT '会话ID(UUID)',
    customer_id BIGINT NOT NULL COMMENT '客户ID',
    agent_id BIGINT COMMENT '坐席ID',
    status TINYINT DEFAULT 1 COMMENT '状态: 1-进行中, 2-已结束, 3-已超时',
    start_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '开始时间',
    end_time DATETIME COMMENT '结束时间',
    message_count INT DEFAULT 0 COMMENT '消息数量',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    
    UNIQUE KEY uk_session_id (session_id),
    INDEX idx_customer_id (customer_id),
    INDEX idx_agent_id (agent_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会话表';
```

#### 4. cs_chat_msg (消息表)

```sql
CREATE TABLE cs_chat_msg (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    msg_id VARCHAR(64) NOT NULL COMMENT '消息ID(UUID)',
    session_id VARCHAR(64) NOT NULL COMMENT '会话ID',
    sender_type TINYINT NOT NULL COMMENT '发送者类型: 1-用户, 2-坐席, 3-AI',
    sender_id BIGINT COMMENT '发送者ID',
    msg_type TINYINT NOT NULL COMMENT '消息类型: 1-文本, 2-图片, 3-文件',
    content TEXT NOT NULL COMMENT '消息内容',
    extra_data JSON COMMENT '扩展数据',
    is_read TINYINT DEFAULT 0 COMMENT '是否已读',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    
    UNIQUE KEY uk_msg_id (msg_id),
    INDEX idx_session_id (session_id),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息表';
```

#### 5. cs_knowledge_faq (知识库 FAQ 表)

```sql
CREATE TABLE cs_knowledge_faq (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    question VARCHAR(512) NOT NULL COMMENT '问题',
    answer TEXT NOT NULL COMMENT '答案',
    category VARCHAR(64) COMMENT '分类',
    tags VARCHAR(256) COMMENT '标签(逗号分隔)',
    milvus_id VARCHAR(64) COMMENT 'Milvus向量ID',
    view_count INT DEFAULT 0 COMMENT '查看次数',
    helpful_count INT DEFAULT 0 COMMENT '有用次数',
    status TINYINT DEFAULT 1 COMMENT '状态: 0-禁用, 1-启用',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    
    INDEX idx_category (category),
    INDEX idx_status (status),
    FULLTEXT INDEX ft_question (question)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库FAQ表';
```

#### 6. cs_work_order (工单表)

```sql
CREATE TABLE cs_work_order (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    order_no VARCHAR(32) NOT NULL COMMENT '工单编号',
    title VARCHAR(256) NOT NULL COMMENT '工单标题',
    description TEXT COMMENT '问题描述',
    customer_id BIGINT NOT NULL COMMENT '客户ID',
    agent_id BIGINT COMMENT '处理坐席ID',
    type TINYINT COMMENT '工单类型: 1-咨询, 2-投诉, 3-建议, 4-故障',
    priority TINYINT DEFAULT 2 COMMENT '优先级: 1-低, 2-中, 3-高, 4-紧急',
    status TINYINT DEFAULT 1 COMMENT '状态: 1-待处理, 2-处理中, 3-待跟进, 4-已解决, 5-已关闭',
    attachments JSON COMMENT '附件列表',
    resolved_time DATETIME COMMENT '解决时间',
    closed_time DATETIME COMMENT '关闭时间',
    remark TEXT COMMENT '备注',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    
    UNIQUE KEY uk_order_no (order_no),
    INDEX idx_customer_id (customer_id),
    INDEX idx_agent_id (agent_id),
    INDEX idx_status (status),
    INDEX idx_priority (priority),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工单表';
```

---

## 🚀 迁移执行流程

### 手动迁移步骤

#### 1. 备份数据库

```bash
# 完整备份
mysqldump -u root -p ai_cs_db > backup_$(date +%Y%m%d_%H%M%S).sql

# 仅备份结构
mysqldump -u root -p --no-data ai_cs_db > schema_backup.sql
```

#### 2. 执行迁移脚本

```bash
# 连接到数据库
mysql -u root -p ai_cs_db

# 执行迁移脚本
source /path/to/V1.1.0__add_customer_phone_index.sql;
```

或使用命令行：
```bash
mysql -u root -p ai_cs_db < V1.1.0__add_customer_phone_index.sql
```

#### 3. 验证迁移结果

```sql
-- 检查索引是否创建
SHOW INDEX FROM cs_customer;

-- 检查表结构
DESCRIBE cs_customer;

-- 验证数据完整性
SELECT COUNT(*) FROM cs_customer;
```

#### 4. 记录迁移日志

创建迁移记录表：
```sql
CREATE TABLE db_migration_history (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    version VARCHAR(20) NOT NULL COMMENT '版本号',
    description VARCHAR(256) NOT NULL COMMENT '描述',
    script_name VARCHAR(128) NOT NULL COMMENT '脚本名称',
    executed_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '执行时间',
    execution_time_ms INT COMMENT '执行耗时(ms)',
    status VARCHAR(20) COMMENT '状态: SUCCESS, FAILED',
    error_message TEXT COMMENT '错误信息',
    executed_by VARCHAR(64) COMMENT '执行人',
    
    UNIQUE KEY uk_version (version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据库迁移历史';
```

记录迁移：
```sql
INSERT INTO db_migration_history 
(version, description, script_name, execution_time_ms, status, executed_by)
VALUES 
('1.1.0', 'Add customer phone index', 'V1.1.0__add_customer_phone_index.sql', 150, 'SUCCESS', 'admin');
```

---

## 📊 常见迁移场景

### 场景 1: 添加字段

```sql
-- V1.3.0__add_customer_address.sql
START TRANSACTION;

ALTER TABLE cs_customer 
ADD COLUMN address VARCHAR(512) COMMENT '地址' AFTER company,
ADD COLUMN city VARCHAR(64) COMMENT '城市' AFTER address;

COMMIT;
```

### 场景 2: 修改字段类型

```sql
-- V1.4.0__modify_phone_length.sql
START TRANSACTION;

ALTER TABLE cs_customer 
MODIFY COLUMN phone VARCHAR(32) COMMENT '手机号';

COMMIT;
```

### 场景 3: 添加索引

```sql
-- V1.5.0__add_composite_index.sql
START TRANSACTION;

CREATE INDEX idx_customer_company_name 
ON cs_customer(company, name);

COMMIT;
```

### 场景 4: 数据迁移

```sql
-- V1.6.0__migrate_old_data.sql
START TRANSACTION;

-- 将旧的状态值映射到新值
UPDATE cs_work_order 
SET status = 4 
WHERE status = 3 AND resolved_time IS NOT NULL;

-- 填充默认值
UPDATE cs_customer 
SET city = '未知' 
WHERE city IS NULL;

COMMIT;
```

### 场景 5: 删除字段

```sql
-- V1.7.0__drop_unused_field.sql
START TRANSACTION;

-- 先确认字段不再使用
-- SELECT COUNT(*) FROM cs_customer WHERE old_field IS NOT NULL;

ALTER TABLE cs_customer DROP COLUMN old_field;

COMMIT;
```

---

## ⚠️ 注意事项

### 1. 向后兼容

- ✅ 只添加新字段，不删除旧字段（至少保留一个版本）
- ✅ 新字段设置默认值或允许 NULL
- ✅ 避免修改现有字段的类型和约束

### 2. 大表操作

对于大数据量表（>100万行）：

```sql
-- 使用在线 DDL (MySQL 5.6+)
ALTER TABLE cs_chat_msg 
ADD COLUMN new_column VARCHAR(128), 
ALGORITHM=INPLACE, 
LOCK=NONE;
```

### 3. 事务管理

```sql
-- 始终使用事务
START TRANSACTION;

-- 执行迁移操作
ALTER TABLE ...

-- 验证结果
SELECT ...

-- 提交或回滚
COMMIT; -- 或 ROLLBACK;
```

### 4. 索引优化

```sql
-- 添加索引前评估影响
EXPLAIN SELECT * FROM cs_customer WHERE phone = '13800138000';

-- 监控索引大小
SELECT 
    table_name,
    index_name,
    ROUND(stat_value * @@innodb_page_size / 1024 / 1024, 2) AS size_mb
FROM mysql.innodb_index_stats
WHERE table_name = 'cs_customer';
```

---

## 🔍 故障排查

### 问题 1: 迁移脚本执行失败

**解决方案**:
```sql
-- 查看错误详情
SHOW WARNINGS;

-- 回滚事务
ROLLBACK;

-- 修复脚本后重新执行
```

### 问题 2: 数据不一致

**解决方案**:
```sql
-- 从备份恢复
mysql -u root -p ai_cs_db < backup_20240115.sql

-- 或使用时间点恢复
mysqlbinlog --stop-datetime="2024-01-15 10:00:00" binlog.000001 | mysql -u root -p ai_cs_db
```

### 问题 3: 锁表问题

**解决方案**:
```sql
-- 查看锁等待
SELECT * FROM information_schema.innodb_lock_waits;

-- 杀死阻塞的进程
KILL <process_id>;

-- 使用在线 DDL 避免锁表
ALTER TABLE ... ALGORITHM=INPLACE, LOCK=NONE;
```

---

## 📚 最佳实践

### 1. 版本控制

- 所有迁移脚本纳入 Git 版本控制
- 使用语义化版本号
- 保持脚本不可变（不要修改已提交的脚本）

### 2. 测试流程

```
开发环境 → 测试环境 → 预生产环境 → 生产环境
```

每个环境都要执行完整的迁移测试。

### 3. 回滚计划

为每个迁移脚本准备回滚脚本：

```sql
-- V1.1.0__add_customer_phone_index.sql (正向)
CREATE INDEX idx_phone ON cs_customer(phone);

-- V1.1.0__rollback.sql (回滚)
DROP INDEX idx_phone ON cs_customer;
```

### 4. 文档化

- 在脚本头部添加详细说明
- 记录迁移原因和影响
- 更新 schema.md 文档

### 5. 自动化

考虑引入 Flyway 或 Liquibase 实现自动化迁移管理。

---

## 📖 相关文档

- [数据库设计文档](schema.md)
- [初始化脚本](init.sql)
- [快速开始指南](../QUICK_START.md)
- [配置管理指南](../deployment/configuration.md)

