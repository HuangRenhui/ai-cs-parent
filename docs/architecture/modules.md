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
├── pom.xml                                # Maven 父 POM 配置
├── README.md                              # 英文项目文档
├── README_CN.md                           # 中文项目文档
│
├── docs/                                  # 项目文档目录
│   ├── README.md                         # 文档索引
│   ├── 功能开发清单.md                     # 全功能清单与状态追踪 ⭐
│   ├── QUICK_START.md                    # 快速开始指南
│   ├── architecture/                     # 架构文档
│   ├── development/                      # 开发文档
│   ├── deployment/                       # 部署文档
│   ├── database/                         # 数据库文档
│   └── api/                              # API文档（250+接口）
│
├── ai-cs-common/                          # 公共模块（JAR库）
│   └── 统一返回、异常处理、DTO、枚举、安全注解、工具类、数据导出、消息通知
│
├── ai-cs-api/                             # API 接口模块（JAR库）
│   └── 4个Feign接口 + 4个Fallback降级实现
│
├── ai-cs-gateway/                         # 网关服务 (:8080)
│   └── 路由转发、JWT认证、请求日志、令牌桶限流、Prometheus
│
├── ai-cs-base-service/                    # 基础服务 (:9001)
│   └── RBAC权限、客户管理、数据统计、系统配置、操作日志
│
├── ai-cs-agent/                           # AI 代理服务 (:9002)
│   └── 智能对话、多模态、工具链、意图识别
│
├── ai-cs-knowledge/                       # 知识库服务 (:9003) - 最大模块
│   └── RAG、FAQ、图片/音频/文件管理、知识图谱、多模态、增强功能
│
├── ai-cs-workorder/                       # 工单服务 (:9004)
│   └── 工单CRUD、智能分类、Flowable流程引擎
│
├── ai-cs-websocket/                       # WebSocket 服务 (:9005)
│   └── 实时通信、消息推送、AI自动回复
│
├── ai-cs-job/                             # 定时任务服务 (:8086)
│   └── 会话清理、数据统计、缓存预热、健康检查
│
└── ai-cs-frontend/                        # 前端应用 (Vue3)
    └── 登录、Dashboard、聊天、客户、知识库、工单管理、移动端适配

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
**依赖**: ai-cs-common, MyBatis Plus, MySQL, Spring Security, jjwt  

**核心功能**:
- **认证与授权**：Spring Security + JWT 认证体系，登录/登出/用户信息/菜单获取
- **用户管理**：CRUD + BCrypt密码加密 + 分页查询
- **角色管理**：CRUD + 角色编码唯一 + 角色-菜单关联
- **菜单权限管理**：树形结构 + 按钮权限 + 角色-菜单关联
- **客户管理**：客户信息 CRUD
- **数据统计**：系统概览、仪表盘数据、聊天趋势、工单统计、客户增长趋势
- **系统配置**：键值对配置管理
- **操作日志**：操作日志分页查询（按用户/模块筛选）

**Controller层**:
- `AuthController` (`/auth`)：认证接口（login、userinfo、menus、logout）
- `UserController` (`/system/user`)：用户CRUD + 分页查询
- `RoleController` (`/system/role`)：角色CRUD
- `MenuController` (`/system/menu`)：菜单树 + CRUD
- `CustomerController` (`/customer`)：客户列表/新增
- `StatisticsController` (`/statistics`)：数据统计（overview/dashboard/chatTrend/workOrderStats/customerTrend）
- `SysConfigController` (`/system/config`)：配置管理
- `LogController` (`/system/log`)：操作日志

**数据表**:
- cs_user、cs_role、cs_menu、cs_user_role、cs_role_menu
- cs_customer、cs_agent、cs_chat_session、cs_chat_msg
- cs_operation_log、cs_statistics、cs_sys_config

---

### 4.2 ai-cs-agent (AI 代理服务)
**类型**: Spring Boot 应用  
**端口**: 9002  
**依赖**: ai-cs-common, ai-cs-api, LangChain4j  

**核心功能**:
- **智能对话**：LLM 模型调用、意图识别、上下文管理、Function Call
- **多模态对话**：图片理解、语音转文字(ASR)、文字转语音(TTS)、视频理解（含降级方案）
- **工具链编排**：多步骤任务执行、工具执行结果缓存
- **工具管理**：可用工具列表查询、缓存清除
- **AiToolEnum 扩展**：查询FAQ、转人工、查询知识库等

**Controller层**:
- `AiChatController` (`/ai`)：9个接口（文本对话、多模态、工具链、缓存管理）

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
**依赖**: ai-cs-common, LangChain4j, Chroma/Milvus SDK, Thumbnailator, FFmpeg/JAVE2  

**核心功能**:
- **FAQ 知识库管理**：传统 CRUD + 自动向量化 + Milvus/Chroma 语义检索 + 批量向量化
- **RAG 检索增强生成**：
  - 多格式文档上传与解析（PDF、TXT、DOCX、MD）
  - 智能文本切片（可配置 chunkSize 和 overlap）
  - 向量化存储 + 两阶段检索（向量召回 + Rerank 重排）
  - 基于 Ollama 本地大模型的智能问答
  - 多用户会话隔离（Redis 持久化）、防幻觉 Prompt 模板
- **文档版本管理**：版本跟踪、回退、对比（MD5/大小）、去重检测、状态管理
- **图片管理**：上传、压缩、格式转换、尺寸调整、裁剪、水印、元数据提取
- **图片增强**：对象存储集成（本地/MinIO/OSS/COS）、CDN加速、批量处理、OCR识别、AI审核、智能标签、感知哈希去重、版本管理、防盗链、访问统计
- **音频管理**：上传、压缩、格式转换、比特率调整、时长提取、真实波形图生成
- **音频增强**：Whisper ASR语音识别、AI内容审核、智能标签、音频指纹去重、版本管理、访问统计
- **文件管理**：预览（图片/PDF/音频/视频）、断点续传下载、批量打包ZIP下载、多格式解压（ZIP/7Z/TAR/GZ/BZ2/TGZ）、Zip炸弹防护
- **多模态知识库**：图片/音频/视频知识入库和问答、混合模态检索（RRF/加权/线性融合）、跨模态语义搜索
- **知识图谱**：节点/关系管理、LLM实体关系自动抽取、图谱可视化（ECharts格式）、图谱问答、子图谱查询、Neo4j/NebulaGraph双数据库支持
- **知识库增强**：3D模型支持、Neo4j集成（Cypher查询/社区检测/PageRank）、图谱推理引擎、时序知识图谱、多语言图谱融合、Graph Embedding（链接预测/节点聚类）、GraphRAG、图谱演化

**Controller层（12个）**:
- `FaqController` (`/knowledge`)：FAQ CRUD + 向量化 + 语义检索
- `RagController` (`/api/rag`)：RAG对话 + 文档上传 + 会话记忆管理
- `DocumentVersionController` (`/api/document/version`)：版本管理
- `ImageController` (`/api/image`)：图片上传/处理/向量化/搜索
- `ImageEnhanceController` (`/api/image/enhance`)：图片增强功能（30+接口）
- `AudioController` (`/api/audio`)：音频上传/处理/向量化/搜索
- `AudioEnhanceController` (`/api/audio/enhance`)：音频增强功能（20+接口）
- `FileController` (`/api/file`)：文件预览/下载/解压
- `KnowledgeGraphController` (`/api/knowledge-graph`)：知识图谱管理
- `MultimodalKnowledgeController` (`/api/multimodal-knowledge`)：多模态知识库
- `MultimodalSearchController` (`/api/multimodal`)：多模态检索
- `KnowledgeEnhancedController` (`/api/knowledge-enhanced`)：增强功能（3D/Neo4j/推理/GraphRAG等）

**数据表**:
- cs_knowledge_faq、cs_document_version
- cs_knowledge_graph_node、cs_knowledge_graph_relation、cs_multimodal_knowledge

**外部依赖**:
- Ollama 服务（LLM、Embedding、Rerank 模型）
- Chroma 向量数据库（本地开发推荐）
- Milvus 向量数据库（生产环境推荐）
- Redis（对话记忆持久化）
- Neo4j/NebulaGraph（图数据库，可选）

---

### 4.4 ai-cs-workorder (工单服务)
**类型**: Spring Boot 应用  
**端口**: 9004  
**依赖**: ai-cs-common, ai-cs-api, MyBatis-Plus, Flowable 7.0.0  

**核心功能**:
- **工单CRUD**：创建、分页查询、详情、更新、删除
- **工单流转**：分配坐席、状态更新（待处理→处理中→已完成→已关闭）
- **智能处理**：相似工单推荐（关键词匹配）、工单智能分类（退款/投诉/咨询/建议/物流）
- **Flowable 流程引擎**：BPMN流程定义（6个节点/4条条件分支）、启动流程、审批任务、查询状态、流程历史、取消流程

**Controller层**:
- `WorkOrderController` (`/workorder`)：11个接口（CRUD + 分配 + 状态 + 相似推荐 + 智能分类）
- `WorkOrderFlowController` (`/workorder/flow`)：7个接口（启动/审批/查询/取消流程等）

**数据表**:
- cs_work_order

---

### 4.5 ai-cs-websocket (WebSocket 服务)
**类型**: Spring Boot 应用  
**端口**: 9005  
**依赖**: ai-cs-common  

**核心功能**:
- WebSocket 连接管理（基于 Spring WebSocket / JSR-356）
- 实时消息推送
- 在线客服聊天
- 消息广播
- AI 自动回复集成
- 心跳检测与断线重连

---

### 4.6 ai-cs-gateway (网关服务)
**类型**: Spring Boot 应用  
**端口**: 8080  
**依赖**: Spring Cloud Gateway, jjwt  

**核心功能**:
- 统一请求入口 + 路由转发
- JWT 全局认证过滤器（白名单机制）
- 请求日志过滤器（记录路径、方法、耗时）
- 下游请求头注入（X-User-Id, X-Username）
- IP/用户双维度 Redis 令牌桶限流
- Prometheus 指标暴露

**路由列表**: 覆盖 `/customer/**`, `/workorder/**`, `/auth/**`, `/statistics/**`, `/system/**`, `/session/**`, `/ai/**`, `/knowledge/**`, `/api/**`

---

### 4.7 ai-cs-job (定时任务服务)
**类型**: Spring Boot 应用  
**端口**: 8086  
**依赖**: ai-cs-common, JdbcTemplate, RedisTemplate  

**核心功能**:
- **会话清理**：每天凌晨2点清理N天前过期会话及关联消息
- **数据统计**：每小时汇总会话/工单/客户数据，每日凌晨0:05生成日报 upsert
- **缓存预热**：每天凌晨3点预热热门FAQ、系统配置、工单统计到 Redis
- **健康检查**：每5分钟数据库连接检测 + 连续失败告警 + 操作日志记录
---

## 5. ai-cs-frontend (前端应用)
**类型**: Vue3 应用  
**端口**: 5173 (开发环境 Vite 默认)  
**技术栈**: Vue3 + Vite + Element Plus + ECharts 5.5 + Axios  

**核心功能**:
- **登录页面**：美观的渐变背景设计 + JWT Token 认证
- **Dashboard 数据大屏**：统计卡片 + ECharts 图表（会话趋势/工单分布/客户增长）
- **AI 聊天界面**：多轮对话 + 上下文管理
- **客户管理界面**：客户列表 + 新增/编辑
- **知识库管理界面**：FAQ 管理 + 文档上传
- **工单管理界面**：工单列表 + 状态流转 + 分配
- **路由守卫**：未登录跳转登录页
- **Token 拦截器**：请求自动携带 + 401 跳转
- **移动端响应式适配**：768px/1024px 断点 + 抽屉菜单 + 触摸优化 + 安全区域

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
ai-cs-common (基础依赖 - JWT工具、统一结果、异常处理、DTO)
    ↑
    ├─ ai-cs-api (Feign 接口定义 + Fallback)
    │     ↑
    │     ├─ ai-cs-agent        (调用 KnowledgeFeign / WorkOrderFeign)
    │     ├─ ai-cs-workorder    (调用 BaseServiceFeign)
    │     └─ ai-cs-base-service (调用 KnowledgeFeign)
    │
    ├─ ai-cs-gateway    (JWT过滤器 + 路由配置)
    ├─ ai-cs-knowledge  (12个Controller, 最大模块)
    ├─ ai-cs-websocket  (独立服务, 无Feign依赖)
    ├─ ai-cs-job        (独立服务, JdbcTemplate + RedisTemplate)
    └─ ai-cs-frontend   (独立构建, 通过网关访问后端)
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
