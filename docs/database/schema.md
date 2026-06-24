# 数据库设计说明

## 概述

AI 智能客服系统使用 MySQL 8.0+ 作为主数据库，Milvus 作为向量数据库。

## 数据库列表

### MySQL 数据库

**数据库名称**: `ai_customer_service`

**字符集**: `utf8mb4`

**排序规则**: `utf8mb4_unicode_ci`

### 数据表清单

| 表名 | 说明 | 所属模块 |
|------|------|----------|
| cs_customer | 客户信息表 | ai-cs-base-service |
| cs_agent | 坐席信息表 | ai-cs-base-service |
| cs_chat_session | 聊天会话表 | ai-cs-base-service |
| cs_chat_msg | 聊天消息表 | ai-cs-base-service |
| cs_knowledge_faq | 知识库FAQ表 | ai-cs-knowledge |
| cs_work_order | 工单表 | ai-cs-workorder |
| cs_document_version | 文档版本管理表 | ai-cs-knowledge |

## 表结构详情

### 1. cs_customer (客户信息表)

存储客户的基本信息和标签。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 客户ID（主键） |
| phone | VARCHAR(20) | 手机号 |
| nickname | VARCHAR(100) | 昵称 |
| avatar | VARCHAR(500) | 头像URL |
| customer_tag | VARCHAR(200) | 客户标签 |
| del_flag | TINYINT | 删除标记：0-未删除，1-已删除 |
| create_time | DATETIME | 创建时间 |
| update_time | DATETIME | 更新时间 |

**索引**:
- PRIMARY KEY: `id`
- INDEX: `idx_phone`
- INDEX: `idx_del_flag`

### 2. cs_agent (坐席信息表)

存储客服坐席的账号和状态信息。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 坐席ID（主键） |
| agent_account | VARCHAR(50) | 坐席账号（唯一） |
| agent_pwd | VARCHAR(100) | 坐席密码（加密存储） |
| agent_name | VARCHAR(50) | 坐席姓名 |
| agent_status | TINYINT | 坐席状态：0-离线，1-在线，2-忙碌 |
| del_flag | TINYINT | 删除标记：0-未删除，1-已删除 |
| create_time | DATETIME | 创建时间 |
| update_time | DATETIME | 更新时间 |

**索引**:
- PRIMARY KEY: `id`
- UNIQUE KEY: `uk_agent_account`
- INDEX: `idx_agent_status`
- INDEX: `idx_del_flag`

### 3. cs_chat_session (聊天会话表)

记录每次聊天的会话信息，支持 AI 客服和人工客服。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 会话ID（主键） |
| session_id | VARCHAR(64) | 会话唯一标识 |
| customer_id | BIGINT | 客户ID |
| agent_id | BIGINT | 坐席ID（可为空） |
| session_type | TINYINT | 会话类型：1-AI客服，2-人工客服 |
| session_status | TINYINT | 会话状态：1-进行中，2-已结束 |
| start_time | DATETIME | 开始时间 |
| end_time | DATETIME | 结束时间 |
| create_time | DATETIME | 创建时间 |
| update_time | DATETIME | 更新时间 |

**索引**:
- PRIMARY KEY: `id`
- UNIQUE KEY: `uk_session_id`
- INDEX: `idx_customer_id`
- INDEX: `idx_agent_id`
- INDEX: `idx_start_time`

### 4. cs_chat_msg (聊天消息表)

存储聊天过程中的所有消息记录。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 消息ID（主键） |
| session_id | VARCHAR(64) | 会话ID |
| msg_content | TEXT | 消息内容 |
| msg_type | TINYINT | 消息类型：1-文本，2-图片，3-文件，4-表情 |
| create_time | DATETIME | 创建时间 |
| update_time | DATETIME | 更新时间 |

**索引**:
- PRIMARY KEY: `id`
- INDEX: `idx_session_id`
- INDEX: `idx_create_time`

### 5. cs_knowledge_faq (知识库FAQ表)

存储常见问题和答案，支持向量化检索。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | FAQ ID（主键） |
| question | VARCHAR(500) | 问题 |
| answer | TEXT | 答案 |
| category | VARCHAR(100) | 分类 |
| sort_num | INT | 排序号 |
| status | TINYINT | 状态：0-禁用，1-启用 |
| milvus_id | VARCHAR(100) | Milvus向量数据库中的记录ID |
| del_flag | TINYINT | 删除标记：0-未删除，1-已删除 |
| create_time | DATETIME | 创建时间 |
| update_time | DATETIME | 更新时间 |

**索引**:
- PRIMARY KEY: `id`
- INDEX: `idx_category`
- INDEX: `idx_status`
- INDEX: `idx_milvus_id`
- INDEX: `idx_del_flag`

### 6. cs_work_order (工单表)

管理客户服务工单的创建、流转和处理。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 工单ID（主键） |
| order_no | VARCHAR(50) | 工单编号（唯一） |
| session_id | VARCHAR(64) | 关联会话ID |
| customer_id | BIGINT | 客户ID |
| order_type | VARCHAR(50) | 工单类型 |
| order_content | TEXT | 工单内容 |
| order_status | TINYINT | 工单状态：1-待处理，2-处理中，3-已完成，4-已关闭 |
| agent_id | BIGINT | 处理坐席ID |
| create_time | DATETIME | 创建时间 |
| update_time | DATETIME | 更新时间 |

**索引**:
- PRIMARY KEY: `id`
- UNIQUE KEY: `uk_order_no`
- INDEX: `idx_session_id`
- INDEX: `idx_customer_id`
- INDEX: `idx_order_status`
- INDEX: `idx_agent_id`
- INDEX: `idx_create_time`

### 7. cs_document_version (文档版本管理表)

管理文档的版本历史，支持版本跟踪、回退和对比。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键ID |
| document_id | VARCHAR(64) | 文档唯一标识（文件名或文档ID） |
| document_name | VARCHAR(256) | 文档名称 |
| version | INT | 版本号 |
| file_path | VARCHAR(512) | 文件路径 |
| file_size | BIGINT | 文件大小（字节） |
| file_type | VARCHAR(32) | 文件类型（PDF/TXT/DOCX/MD等） |
| file_md5 | VARCHAR(64) | 文件MD5哈希值，用于内容去重 |
| milvus_collection_id | VARCHAR(128) | Milvus向量集合ID |
| segment_count | INT | 文档片段数量 |
| version_description | VARCHAR(512) | 版本描述 |
| is_current | TINYINT | 是否为当前版本：0-否，1-是 |
| status | TINYINT | 状态：0-草稿，1-已发布，2-已归档 |
| uploader_id | BIGINT | 上传人ID |
| uploader_name | VARCHAR(64) | 上传人姓名 |
| del_flag | TINYINT | 删除标记：0-未删除，1-已删除 |
| create_time | DATETIME | 创建时间 |
| update_time | DATETIME | 更新时间 |

**索引**:
- PRIMARY KEY: `id`
- UNIQUE KEY: `uk_document_version` (`document_id`, `version`)
- INDEX: `idx_document_id`
- INDEX: `idx_is_current`
- INDEX: `idx_file_md5`
- INDEX: `idx_status`
- INDEX: `idx_del_flag`
- INDEX: `idx_create_time`

## Milvus 向量数据库

### Collection: faq_vectors

用于存储 FAQ 问题的向量表示，支持语义相似度检索。

**字段**:
- `id`: VARCHAR - Milvus 内部ID
- `faq_id`: BIGINT - 关联的 FAQ ID
- `vector`: FLOAT_VECTOR - 768维向量（根据 embedding 模型）
- `question`: VARCHAR - 问题文本（用于展示）

**索引**:
- IVF_FLAT 索引用于向量相似度搜索

## 数据库初始化

执行以下命令初始化数据库：

```bash
mysql -u root -p < docs/database/init.sql
```

## 注意事项

1. **密码安全**: 生产环境必须对坐席密码进行加密存储
2. **数据备份**: 定期备份数据库，建议每日全量备份
3. **索引优化**: 根据实际查询场景优化索引
4. **软删除**: 所有业务表使用 `del_flag` 实现逻辑删除
5. **时间戳**: 使用 `create_time` 和 `update_time` 自动维护记录时间
