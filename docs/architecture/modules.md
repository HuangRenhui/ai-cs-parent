# 模块详细说明

本文档详细说明各模块的职责、接口定义和数据表结构。

## 📋 目录

- [模块依赖关系](#-模块依赖关系)
- [公共模块](#1-ai-cs-common-公共模块)
- [API 模块](#2-ai-cs-api-接口模块)
- [网关服务](#3-ai-cs-gateway-网关服务)
- [业务服务](#4-业务服务模块)
- [前端应用](#5-ai-cs-frontend-前端应用)
- [开发建议](#-开发建议)

---

## 🔗 模块依赖关系

```
ai-cs-parent/                              # 项目根目录
├── .gitignore                             # Git 忽略文件配置
├── pom.xml                                # Maven 父 POM 配置
├── README.md                              # 英文项目文档
├── README_CN.md                           # 中文项目文档
│
├── docs/                                  # 项目文档目录
│   ├── README.md                         # 文档索引
│   ├── database/                         # 数据库文档
│   │   ├── init.sql                      # 数据库初始化脚本
│   │   └── schema.md                     # 数据库设计说明
│   ├── architecture/                     # 架构文档
│   │   └── overview.md                   # 架构概览
│   ├── development/                      # 开发文档
│   │   └── standards.md                  # 代码规范
│   ├── deployment/                       # 部署文档（待补充）
│   └── api/                              # API 文档（待补充）
│
├── ai-cs-common/                          # 公共模块
│   ├── pom.xml
│   └── src/main/java/com/ai/cs/common/
│       ├── config/                        # 配置类
│       │   └── MyMetaObjectHandler.java  # MyBatis 自动填充
│       ├── constant/                      # 常量定义
│       │   └── RedisKeyConst.java        # Redis Key 常量
│       ├── dto/                           # 数据传输对象
│       │   ├── ChatDTO.java
│       │   ├── IntentDTO.java
│       │   └── WorkOrderDTO.java
│       ├── entity/                        # 实体基类
│       │   └── BaseEntity.java
│       ├── enums/                         # 枚举类
│       │   ├── IntentEnum.java
│       │   └── MsgTypeEnum.java
│       ├── exception/                     # 异常处理
│       │   └── GlobalExceptionHandler.java
│       └── result/                        # 返回结果
│           └── Result.java
│
├── ai-cs-api/                             # API 接口模块
│   ├── pom.xml
│   └── src/main/java/com/ai/cs/api/feign/
│       ├── AiAgentFeign.java             # AI 代理 Feign 接口
│       └── WorkOrderFeign.java           # 工单 Feign 接口
│
├── ai-cs-gateway/                         # 网关服务
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/ai/cs/gateway/
│       │   └── GatewayApplication.java   # 启动类
│       └── resources/
│           └── application.yml           # 配置文件
│
├── ai-cs-base-service/                    # 基础服务
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/ai/cs/base/
│       │   ├── controller/               # 控制器层
│       │   │   └── CustomerController.java
│       │   ├── entity/                   # 实体类
│       │   │   ├── Agent.java
│       │   │   ├── ChatMsg.java
│       │   │   ├── ChatSession.java
│       │   │   └── Customer.java
│       │   ├── mapper/                   # 数据访问层
│       │   │   └── CustomerMapper.java
│       │   ├── service/                  # 业务逻辑层
│       │   │   └── CustomerService.java
│       │   └── BaseServiceApplication.java
│       └── resources/
│           └── application.yml
│
├── ai-cs-ai-agent/                        # AI 代理服务
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/ai/cs/aiagent/
│       │   ├── config/                   # 配置类
│       │   │   └── LlmProperties.java
│       │   ├── controller/               # 控制器
│       │   │   └── AiChatController.java
│       │   ├── enums/                    # 枚举
│       │   │   └── AiToolEnum.java
│       │   ├── service/                  # 业务逻辑
│       │   │   └── AiAgentService.java
│       │   ├── util/                     # 工具类
│       │   │   └── LlmUtil.java
│       │   └── AiAgentApplication.java
│       └── resources/
│           └── application.yml
│
├── ai-cs-knowledge/                       # 知识库服务
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/ai/cs/knowledge/
│       │   ├── controller/               # 控制器
│       │   │   └── FaqController.java
│       │   ├── entity/                   # 实体类
│       │   │   └── KnowledgeFaq.java
│       │   ├── mapper/                   # 数据访问
│       │   │   └── KnowledgeFaqMapper.java
│       │   ├── service/                  # 业务逻辑
│       │   │   ├── FaqService.java
│       │   │   └── VectorService.java
│       │   ├── util/                     # 工具类
│       │   │   ├── EmbeddingUtil.java
│       │   │   ├── MilvusUtil.java
│       │   │   └── VectorUtil.java
│       │   └── KnowledgeApplication.java
│       └── resources/
│           ├── application.yml
│           └── db/migration/
│               └── add_milvus_id_to_faq.sql
│
├── ai-cs-workorder/                       # 工单服务
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/ai/cs/workorder/
│       │   ├── controller/               # 控制器
│       │   │   └── WorkOrderController.java
│       │   ├── entity/                   # 实体类
│       │   │   └── WorkOrder.java
│       │   ├── mapper/                   # 数据访问
│       │   │   └── WorkOrderMapper.java
│       │   ├── service/                  # 业务逻辑
│       │   │   └── WorkOrderService.java
│       │   └── WorkOrderApplication.java
│       └── resources/
│           └── application.yml
│
├── ai-cs-websocket/                       # WebSocket 服务
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/ai/cs/websocket/
│       │   ├── config/                   # 配置类
│       │   │   └── WebSocketConfig.java
│       │   ├── endpoint/                 # WebSocket 端点
│       │   │   └── ChatWebSocket.java
│       │   └── WebSocketApplication.java
│       └── resources/
│           └── application.yml
│
├── ai-cs-job/                             # 定时任务服务
│   ├── pom.xml
│   └── src/main/resources/
│       └── (待补充业务代码)
│
└── ai-cs-frontend/                        # 前端应用
    ├── package.json                       # Node 依赖配置
    ├── vite.config.js                     # Vite 构建配置
    ├── index.html                         # HTML 入口
    └── src/
        ├── main.js                        # 应用入口
        ├── App.vue                        # 主组件
        ├── router/                        # 路由配置
        │   └── index.js
        ├── utils/                         # 工具函数
        │   └── request.js                # HTTP 请求封装
        └── views/                         # 页面组件
            ├── ChatPage.vue              # AI 聊天页面
            ├── CustomerPage.vue          # 客户管理页面
            ├── KnowledgePage.vue         # 知识库管理页面
            └── WorkOrderPage.vue         # 工单管理页面
```

---

## 1. ai-cs-common (公共模块)
**类型**: JAR 库  
**依赖**: 无  
**被依赖**: 所有业务模块  

**提供功能**:
- 统一返回结果封装 (`Result`)
- 全局异常处理 (`GlobalExceptionHandler`)
- 基础实体类 (`BaseEntity`)
- 常用工具类和常量
- MyBatis Plus 自动填充配置

**使用建议**:
- 所有业务模块都应该依赖此模块
- 不要在此模块中编写业务逻辑
- 通用工具类放在这里

---

## 2. ai-cs-api (API 模块)
**类型**: JAR 库  
**依赖**: ai-cs-common, Spring Cloud OpenFeign  
**被依赖**: 需要跨服务调用的模块  

**提供功能**:
- 定义 Feign 客户端接口
- 服务间通信的 DTO 定义

**使用建议**:
- 仅包含接口定义，不包含实现
- 接口路径与 Controller 保持一致
- 避免循环依赖

---

## 3. ai-cs-gateway (网关服务)
**类型**: Spring Boot 应用  
**端口**: 8080  
**依赖**: Spring Cloud Gateway  

**核心功能**:
- 统一请求入口
- 路由转发到各微服务
- 身份认证和授权
- 限流和熔断
- 日志记录

**启动方式**:
```bash
cd ai-cs-gateway
mvn spring-boot:run
```

---

## 4. 业务服务模块

### 4.1 ai-cs-base-service (基础服务)
**类型**: Spring Boot 应用  
**端口**: 9001  
**依赖**: ai-cs-common, MyBatis Plus, MySQL  

**核心功能**:
- 客户信息管理 (CRUD)
- 坐席账号管理
- 聊天会话管理
- 聊天记录存储

**数据表**:
- cs_customer
- cs_agent
- cs_chat_session
- cs_chat_msg

---

### 4.2 ai-cs-ai-agent (AI 代理服务)
**类型**: Spring Boot 应用  
**端口**: 9002  
**依赖**: ai-cs-common, ai-cs-api  

**核心功能**:
- LLM 模型调用
- 智能对话生成
- 意图识别
- Function Call 工具调用
- 上下文管理

**外部依赖**:
- LLM 服务（如 Qwen、ChatGLM 等）

**配置项**:
```yaml
llm:
  url: http://127.0.0.1:8000/v1/chat/completions
  api-key: xxx
  model: qwen-7b
  temperature: 0.2
```

---

### 4.3 ai-cs-knowledge (知识库服务)
**类型**: Spring Boot 应用  
**端口**: 9003  
**依赖**: ai-cs-common, LangChain4j, Chroma/Milvus SDK

**核心功能**:
- FAQ 知识库管理（传统 CRUD）
- **RAG 检索增强生成系统**（新增）
  - 多格式文档上传与解析（PDF、TXT、DOCX、MD）
  - 智能文本切片（可配置 chunkSize 和 overlap）
  - 向量化存储到 Chroma/Milvus
  - 两阶段检索：向量召回 + Rerank 重排
  - 基于 Ollama 本地大模型的智能问答
  - 多用户会话隔离（Redis 持久化）
  - 自定义 Prompt 模板，防幻觉机制
- **文档版本管理**（新增）
  - 文档版本跟踪与记录
  - 版本回退功能
  - 版本对比（基于MD5和文件大小）
  - 内容去重（MD5检测）
  - 版本状态管理（草稿、已发布、已归档）

**数据表**:
- cs_knowledge_faq
- cs_document_version

**外部依赖**:
- Ollama 服务（提供 LLM、Embedding、Rerank 模型）
- Chroma 向量数据库（本地开发推荐）
- Milvus 向量数据库（生产环境推荐）
- Redis（对话记忆持久化）

**关键组件**:
- `RagController`: RAG 相关 API 接口
- `FaqController`: 传统 FAQ 管理接口
- `DocumentVersionController`: 文档版本管理接口
- `DocumentLoadService`: 文档加载与切片服务
- `FileUploadService`: 文件上传服务
- `DocumentVersionService`: 文档版本管理服务
- `RagChatService`: RAG 聊天服务
- `RedisChatMemoryStore`: Redis 对话记忆存储
- `LangChainConfig`: LangChain4j 核心配置
- `RagProperties`: RAG 配置属性

**配置项**:
```yaml
rag:
  ollama:
    base-url: http://localhost:11434
    llm-model: qwen:7b
    embedding-model: nomic-embed-text
    rerank-model: bge-reranker:latest
    temperature: 0.1
  chroma:
    base-url: http://localhost:8000
    collection-name: private_knowledge_base
  split:
    chunk-size: 500
    chunk-overlap: 80
  retrieve:
    top-k: 5
    rerank-top-k: 3
  chat-memory:
    ttl: 604800  # 7天
```

**API 接口**:
- `POST /api/rag/upload/pdf` - 上传 PDF 文档入库
- `POST /api/rag/upload/file` - 上传普通文件入库
- `POST /api/rag/upload/pdf/versioned` - 上传 PDF 文档入库（带版本管理）
- `POST /api/rag/upload/file/versioned` - 上传普通文件入库（带版本管理）
- `POST /api/rag/chat` - 智能问答（支持多轮对话）
- `POST /api/rag/memory/clear/user` - 清空单用户记忆
- `POST /api/rag/memory/clear/all` - 清空全部记忆
- `GET /api/document/version/*` - 文档版本管理接口
- `GET /api/faq/*` - 传统 FAQ CRUD 接口

---

### 4.4 ai-cs-workorder (工单服务)
**类型**: Spring Boot 应用  
**端口**: 9004  
**依赖**: ai-cs-common, ai-cs-api  

**核心功能**:
- 工单创建和管理
- 工单状态流转
- 工单分配
- 工单查询统计

**数据表**:
- cs_work_order

---

### 4.5 ai-cs-websocket (WebSocket 服务)
**类型**: Spring Boot 应用  
**端口**: 9005  
**依赖**: ai-cs-common  

**核心功能**:
- WebSocket 连接管理
- 实时消息推送
- 在线客服聊天
- 消息广播

---

### 4.6 ai-cs-job (定时任务服务)
**类型**: Spring Boot 应用  
**依赖**: ai-cs-common  

**核心功能** (待实现):
- 定时清理过期会话
- 数据统计和报表
- 数据备份
- 定时同步

---

## 5. ai-cs-frontend (前端应用)
**类型**: Vue3 应用  
**端口**: 8080 (开发环境)  
**技术栈**: Vue3 + Vite + Element Plus  

**核心功能**:
- AI 聊天界面
- 客户管理界面
- 知识库管理界面
- 工单管理界面

**启动方式**:
```bash
cd ai-cs-frontend
npm install
npm run dev
```

---

## 📚 相关文档

- [系统架构概览](overview.md) - 架构图、技术栈、部署方案
- [数据流向与业务场景](data-flow.md) - 核心业务流程详解
- [数据库设计](../database/schema.md) - 数据库表结构
- [API 接口文档](../api/README.md) - RESTful API 说明

---

## 💡 开发建议

```
ai-cs-common (基础依赖)
    ↑
    ├─ ai-cs-api (Feign 接口)
    │     ↑
    │     ├─ ai-cs-ai-agent
    │     └─ ai-cs-workorder
    │
    ├─ ai-cs-gateway
    ├─ ai-cs-base-service
    ├─ ai-cs-knowledge
    ├─ ai-cs-websocket
    └─ ai-cs-job
```

### 添加新功能

1. 在对应的业务模块中创建 Entity、Mapper、Service、Controller
2. 如需跨服务调用，在 ai-cs-api 中定义 Feign 接口
3. 在 ai-cs-common 中添加通用的 DTO 或工具类
4. 更新数据库脚本 `docs/database/init.sql`

### 修改公共代码

1. 在 ai-cs-common 中修改
2. 重新编译：`mvn clean install`
3. 其他模块重新加载依赖

### 调试技巧

1. 先启动依赖服务（MySQL、Redis、Milvus）
2. 按顺序启动微服务
3. 使用网关统一访问
4. 查看各服务的日志输出

### 注意事项

- ⚠️ 不要循环依赖模块
- ⚠️ 保持 ai-cs-common 轻量，只放通用代码
- ⚠️ 每个服务独立配置数据库连接
- ⚠️ 生产环境使用配置中心管理配置
- ⚠️ 敏感信息不要硬编码在代码中
