# API 接口文档

> AI 智能客服系统完整 RESTful API 接口文档  
> 基于源码自动生成，与代码保持同步  
> 更新时间：2026-07-20

---

## 目录

- [1. 基础信息](#1-基础信息)
- [2. 认证与授权（Base Service）](#2-认证与授权base-service)
- [3. 用户管理（Base Service）](#3-用户管理base-service)
- [4. 角色管理（Base Service）](#4-角色管理base-service)
- [5. 菜单管理（Base Service）](#5-菜单管理base-service)
- [6. 客户管理（Base Service）](#6-客户管理base-service)
- [7. 系统配置（Base Service）](#7-系统配置base-service)
- [8. 操作日志（Base Service）](#8-操作日志base-service)
- [9. 数据统计（Base Service）](#9-数据统计base-service)
- [10. AI Agent 对话](#10-ai-agent-对话)
- [11. 工单管理（WorkOrder Service）](#11-工单管理workorder-service)
- [12. 工单流程（Flowable）](#12-工单流程flowable)
- [13. 知识库 FAQ](#13-知识库-faq)
- [14. RAG 检索增强生成](#14-rag-检索增强生成)
- [15. Prompt 调试（六大策略）](#15-prompt-调试六大策略)
- [16. 文档版本管理](#16-文档版本管理)
- [17. 图片管理](#17-图片管理)
- [18. 图片增强功能](#18-图片增强功能)
- [19. 音频管理](#19-音频管理)
- [20. 音频增强功能](#20-音频增强功能)
- [21. 文件管理](#21-文件管理)
- [22. 知识图谱](#22-知识图谱)
- [23. 多模态知识库](#23-多模态知识库)
- [24. 多模态检索](#24-多模态检索)
- [25. 知识库增强功能](#25-知识库增强功能)
- [26. Feign 服务间调用接口](#26-feign-服务间调用接口)
- [27. WebSocket 实时通信](#27-websocket-实时通信)
- [28. 错误码说明](#28-错误码说明)

---

## 1. 基础信息

### 服务端口

| 服务 | 端口 | 基础路径 |
|------|------|----------|
| Gateway（网关） | 8080 | `/` |
| Base Service | 9001 | 通过 Gateway 路由 |
| AI Agent | 9002 | 通过 Gateway 路由 |
| Knowledge | 9003 | 通过 Gateway 路由 |
| WorkOrder | 9004 | 通过 Gateway 路由 |
| WebSocket | 9005 | 直连 |

### 通用请求头

```http
Content-Type: application/json
Authorization: Bearer <token>
```

### 统一响应格式

```json
{
  "code": 200,
  "message": "success",
  "data": {}
}
```

**成功响应**：`code` 为 `200`，`data` 包含具体数据  
**失败响应**：`code` 为错误码，`message` 为错误描述

---

## 2. 认证与授权（Base Service）

**Controller**: `AuthController` | **路径前缀**: `/auth`

### 2.1 用户登录

> 无需认证（`@NoAuth`）

```http
POST /auth/login
Content-Type: application/json

{
  "username": "admin",
  "password": "password123"
}
```

**响应**：
```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiJ9...",
    "tokenType": "Bearer",
    "expiresIn": 7200
  }
}
```

### 2.2 获取当前用户信息

```http
GET /auth/userinfo
Authorization: Bearer <token>
```

**响应**：
```json
{
  "code": 200,
  "data": {
    "id": 1,
    "username": "admin",
    "realName": "系统管理员",
    "email": "admin@example.com",
    "avatar": "/avatars/default.png",
    "roles": ["admin", "operator"],
    "permissions": ["system:user:list", "system:role:list", ...]
  }
}
```

### 2.3 获取当前用户菜单

```http
GET /auth/menus
Authorization: Bearer <token>
```

**响应**：返回用户拥有的菜单树

### 2.4 退出登录

```http
POST /auth/logout
Authorization: Bearer <token>
```

---

## 3. 用户管理（Base Service）

**Controller**: `UserController` | **路径前缀**: `/system/user`

权限前缀：`system:user`

### 3.1 用户列表

```http
GET /system/user/list
```

### 3.2 用户分页查询

```http
GET /system/user/page?pageNum=1&pageSize=10
```

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| pageNum | int | 1 | 页码 |
| pageSize | int | 10 | 每页数量 |

### 3.3 创建用户

```http
POST /system/user/save
Content-Type: application/json

{
  "username": "newuser",
  "password": "encrypted_password",
  "realName": "新用户",
  "email": "user@example.com",
  "status": 1
}
```

### 3.4 更新用户

```http
PUT /system/user/update
Content-Type: application/json

{
  "id": 2,
  "realName": "更新后的名称",
  "email": "updated@example.com"
}
```

### 3.5 删除用户

```http
DELETE /system/user/delete/{id}
```

### 3.6 用户详情

```http
GET /system/user/info/{id}
```

---

## 4. 角色管理（Base Service）

**Controller**: `RoleController` | **路径前缀**: `/system/role`

权限前缀：`system:role`

### 4.1 角色列表

```http
GET /system/role/list
```

**响应**：返回所有启用状态的 `Role` 列表

### 4.2 创建角色

```http
POST /system/role/save
Content-Type: application/json

{
  "roleName": "运营专员",
  "roleCode": "operator",
  "description": "负责日常运营工作",
  "status": 1
}
```

### 4.3 更新角色

```http
PUT /system/role/update
Content-Type: application/json

{
  "id": 2,
  "roleName": "高级运营",
  "description": "更新后的描述"
}
```

### 4.4 删除角色

```http
DELETE /system/role/delete/{id}
```

---

## 5. 菜单管理（Base Service）

**Controller**: `MenuController` | **路径前缀**: `/system/menu`

权限前缀：`system:menu`

### 5.1 菜单树

```http
GET /system/menu/tree
```

**响应**：返回树形结构的菜单列表

### 5.2 菜单列表（平铺）

```http
GET /system/menu/list
```

### 5.3 创建菜单

```http
POST /system/menu/save
Content-Type: application/json

{
  "menuName": "用户管理",
  "parentId": 1,
  "path": "/system/user",
  "component": "system/UserList",
  "perms": "system:user:list",
  "type": 1,
  "icon": "user",
  "sort": 1,
  "status": 1
}
```

### 5.4 更新菜单

```http
PUT /system/menu/update
```

### 5.5 删除菜单

```http
DELETE /system/menu/delete/{id}
```

---

## 6. 客户管理（Base Service）

**Controller**: `CustomerController` | **路径前缀**: `/customer`

### 6.1 客户列表

```http
GET /customer/list
```

### 6.2 新增客户

```http
POST /customer/save
Content-Type: application/json

{
  "name": "张三",
  "phone": "13800138000",
  "email": "zhangsan@example.com",
  "company": "某某公司"
}
```

---

## 7. 系统配置（Base Service）

**Controller**: `SysConfigController` | **路径前缀**: `/system/config`

### 7.1 配置列表

```http
GET /system/config/list
```

### 7.2 新增配置

```http
POST /system/config/save
Content-Type: application/json

{
  "configKey": "system.name",
  "configValue": "AI智能客服系统",
  "description": "系统名称",
  "status": 1
}
```

### 7.3 更新配置

```http
PUT /system/config/update
```

### 7.4 按 Key 获取配置值

```http
GET /system/config/get/{key}
```

**示例**：`GET /system/config/get/system.name`

---

## 8. 操作日志（Base Service）

**Controller**: `LogController` | **路径前缀**: `/system/log`

### 8.1 操作日志分页查询

```http
GET /system/log/page?pageNum=1&pageSize=10&userId=1&module=用户管理
```

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| pageNum | int | 否 | 1 | 页码 |
| pageSize | int | 否 | 10 | 每页数量 |
| userId | Long | 否 | - | 按操作人ID筛选 |
| module | String | 否 | - | 按模块筛选 |

---

## 9. 数据统计（Base Service）

**Controller**: `StatisticsController` | **路径前缀**: `/statistics`

### 9.1 系统概览

```http
GET /statistics/overview
```

**响应**：返回今日/本周的会话数、工单数、客户数等概览数据

### 9.2 Dashboard 数据

```http
GET /statistics/dashboard
```

**响应**：返回仪表盘所需的聚合数据（统计卡片 + 图表数据）

### 9.3 聊天趋势统计

```http
POST /statistics/chat-trend
Content-Type: application/json

{
  "startDate": "2026-07-01",
  "endDate": "2026-07-20"
}
```

### 9.4 工单状态/类型分布

```http
GET /statistics/workorder-stats
```

**响应**：按状态和类型统计的工单分布数据

### 9.5 客户增长趋势

```http
POST /statistics/customer-trend
Content-Type: application/json

{
  "startDate": "2026-07-01",
  "endDate": "2026-07-20"
}
```

---

## 10. AI Agent 对话

**Controller**: `AiChatController` | **路径前缀**: `/ai`

### 10.1 文本对话（核心接口）

```http
POST /ai/chat/send
Content-Type: application/json

{
  "sessionId": "session_123",
  "customerId": 1,
  "message": "如何重置密码？",
  "context": { ... }
}
```

**响应**：`Result<String>` - AI 生成的回复文本

### 10.2 图片对话（多模态）

```http
POST /ai/chat/image
Content-Type: application/json

{
  "imageUrl": "https://example.com/image.jpg",
  "question": "这张图片里有什么？"
}
```

### 10.3 语音转文字

```http
POST /ai/chat/speech-to-text
Content-Type: application/json

{
  "audioUrl": "https://example.com/audio.mp3"
}
```

### 10.4 文字转语音

```http
POST /ai/chat/text-to-speech
Content-Type: application/json

{
  "text": "您好，欢迎使用智能客服系统"
}
```

### 10.5 获取可用工具列表

```http
GET /ai/tools/list
```

**响应**：返回 `AiToolEnum` 中定义的所有可用工具

### 10.6 执行工具链

```http
POST /ai/tools/chain
Content-Type: application/json

{
  "sessionId": "session_123",
  "userMsg": "查询最新订单状态",
  "tools": ["queryOrder", "queryFAQ"]
}
```

### 10.7 清除工具缓存

```http
POST /ai/tools/cache/clear
```

---

## 11. 工单管理（WorkOrder Service）

**Controller**: `WorkOrderController` | **路径前缀**: `/workorder`

### 11.1 创建工单

```http
POST /workorder/create
Content-Type: application/json

{
  "customerId": 1,
  "customerName": "张三",
  "title": "无法登录系统",
  "description": "用户反馈登录后提示账号异常",
  "orderType": "投诉",
  "priority": "high",
  "source": "chat"
}
```

**响应**：
```json
{
  "code": 200,
  "message": "工单创建成功，工单号：WO20260720001"
}
```

### 11.2 分页查询工单

```http
GET /workorder/page?pageNum=1&pageSize=10&orderType=投诉&orderStatus=0&agentId=1
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| pageNum | int | 否 | 页码，默认1 |
| pageSize | int | 否 | 每页数量，默认10 |
| orderType | String | 否 | 工单类型筛选 |
| orderStatus | Integer | 否 | 状态：0-待处理,1-处理中,2-已完成,3-已关闭 |
| agentId | Long | 否 | 坐席ID筛选 |

### 11.3 工单详情

```http
GET /workorder/info/{id}
```

### 11.4 更新工单

```http
PUT /workorder/update
Content-Type: application/json

{
  "id": 100,
  "title": "更新后的标题",
  "description": "更新后的描述"
}
```

### 11.5 分配工单给坐席

```http
PUT /workorder/assign/{id}?agentId=2
```

### 11.6 更新工单状态

```http
PUT /workorder/status/{id}?status=1
```

> 状态值：0-待处理, 1-处理中, 2-已完成, 3-已关闭

### 11.7 完成工单

```http
PUT /workorder/complete/{id}
```

> 内部调用 updateStatus(id, 2)

### 11.8 关闭工单

```http
PUT /workorder/close/{id}
```

> 内部调用 updateStatus(id, 3)

### 11.9 删除工单

```http
DELETE /workorder/delete/{id}
```

### 11.10 相似工单推荐

```http
GET /workorder/similar/{id}
```

**响应**：基于关键词匹配的相似工单列表

### 11.11 工单智能分类

```http
POST /workorder/auto-classify/{id}
```

**响应**：返回自动分类结果（退款/投诉/咨询/建议/物流）

---

## 12. 工单流程（Flowable）

**Controller**: `WorkOrderFlowController` | **路径前缀**: `/workorder/flow`  
> **注意**：需要 `flowable.enabled=true` 配置才能启用

### 12.1 启动工单流程

```http
POST /workorder/flow/start/{workOrderId}
```

**响应**：返回流程实例信息

### 12.2 查询工单流程状态

```http
GET /workorder/flow/status/{workOrderId}
```

### 12.3 完成任务（审批/处理）

```http
POST /workorder/flow/complete/{taskId}
Content-Type: application/json

{
  "approved": true,
  "comment": "审核通过"
}
```

### 12.4 查询待办任务列表

```http
GET /workorder/flow/tasks?assignee=zhangsan
```

### 12.5 查询流程定义列表

```http
GET /workorder/flow/definitions
```

### 12.6 获取流程历史记录

```http
GET /workorder/flow/history/{workOrderId}
```

### 12.7 取消工单流程

```http
POST /workorder/flow/cancel/{workOrderId}?reason=客户主动取消
```

---

## 13. 知识库 FAQ

**Controller**: `FaqController` | **路径前缀**: `/knowledge`

### 13.1 FAQ 列表

```http
GET /knowledge/list
```

**响应**：返回所有启用状态的 FAQ 列表

### 13.2 新增 FAQ

```http
POST /knowledge/save
Content-Type: application/json

{
  "question": "如何重置密码？",
  "answer": "您可以通过以下步骤重置密码...",
  "category": "账户管理",
  "tags": "密码,重置,账户",
  "priority": 1,
  "enableFlag": 1
}
```

> 创建时自动向量化并插入 Milvus 向量库

### 13.3 更新 FAQ

```http
PUT /knowledge/update
Content-Type: application/json

{
  "id": 10,
  "question": "更新后的问题",
  "answer": "更新后的答案"
}
```

> 更新时自动增量更新 Milvus 向量

### 13.4 删除 FAQ

```http
DELETE /knowledge/delete/{id}
```

> 同时删除 Milvus 中的对应向量

### 13.5 FAQ 语义检索

```http
GET /knowledge/search?question=忘记密码怎么办
```

**响应**：基于 Milvus 向量数据库的语义检索 + RAG 问答结果

### 13.6 单个 FAQ 向量化

```http
POST /knowledge/vectorize/{id}
```

> 手动触发单个 FAQ 向量化并插入 Milvus

### 13.7 批量 FAQ 向量化

```http
POST /knowledge/vectorize/batch
```

> 将所有启用 FAQ 全量向量化并存入 Milvus

### 13.8 批量增量更新向量

```http
POST /knowledge/vectorize/increment
```

> 增量更新所有 FAQ 的 Milvus 向量数据

---

## 14. RAG 检索增强生成

**Controller**: `RagController` | **路径前缀**: `/api/rag`

### 14.1 用户对话问答（多轮对话）

```http
POST /api/rag/chat?userId=user_001&question=AI客服系统有哪些功能？
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| userId | String | 是 | 用户唯一ID（用于会话隔离） |
| question | String | 是 | 用户提问内容 |

**响应**：基于 LangChain4j + Ollama + Chroma 的 RAG 答案

### 14.2 上传 PDF 文档入库

```http
POST /api/rag/upload/pdf
Content-Type: multipart/form-data

file: document.pdf
```

> 解析 PDF 文件，切片并向量化存入 Chroma 向量库

### 14.3 上传普通文件入库

```http
POST /api/rag/upload/file
Content-Type: multipart/form-data

file: readme.txt (支持 TXT/DOCX/MD 格式)
```

### 14.4 上传 PDF 文档入库（带版本管理）

```http
POST /api/rag/upload/pdf/versioned
Content-Type: multipart/form-data

file: document.pdf
documentId: doc_001
documentName: 产品手册
versionDescription: 初始版本
uploaderId: 1
uploaderName: 管理员
```

### 14.5 上传普通文件入库（带版本管理）

```http
POST /api/rag/upload/file/versioned
Content-Type: multipart/form-data

file: readme.md
documentId: doc_002
documentName: 使用说明
versionDescription: V1.0
uploaderId: 1
uploaderName: 管理员
```

### 14.6 清空单用户对话记忆

```http
POST /api/rag/memory/clear/user?userId=user_001
```

### 14.7 清空全部对话记忆

```http
POST /api/rag/memory/clear/all
```

---

## 15. Prompt 调试（六大策略）

**Controller**: `PromptDebugController` | **路径前缀**: `/api/prompt`

### 15.1 获取当前 Prompt 配置

```http
GET /api/prompt/config
```

**响应**：返回完整的6大策略配置（角色/格式/CoT/Few-shot/边界/上下文），含当前预设名称

### 15.2 获取所有可用预设模板

```http
GET /api/prompt/presets
```

**响应**：
```json
{
  "availablePresets": ["java-engineer", "interviewer", "json-output", "cot-reasoning", "few-shot", "strict-boundary", "table-output", "full-combo"],
  "currentPreset": "java-engineer"
}
```

### 15.3 获取指定预设模板详情

```http
GET /api/prompt/presets/{name}
```

| 参数 | 说明 |
|------|------|
| name | 预设名称：java-engineer / interviewer / json-output / cot-reasoning / few-shot / strict-boundary / table-output / full-combo |

### 15.4 预览 System Prompt

```http
GET /api/prompt/preview/system
```

**响应**：返回当前配置生成的 System Prompt 全文及长度

### 15.5 预览完整 Prompt

```http
POST /api/prompt/preview/full
Content-Type: application/json

{
  "question": "如何优化数据库查询性能？",
  "contextDocs": [
    "参考文档1：索引优化是提升查询性能的关键手段...",
    "参考文档2：慢查询日志可以帮助定位性能瓶颈..."
  ]
}
```

**响应**：返回 systemPrompt、userPrompt、fullPrompt、totalLength、contextDocCount

### 15.6 预览 OpenAI Messages 格式

```http
POST /api/prompt/preview/messages
Content-Type: application/json

{
  "question": "什么是微服务？",
  "contextDocs": ["微服务是一种架构风格..."]
}
```

**响应**：返回完整的 messages 数组（OpenAI兼容格式），含 messageCount 和 totalLength

### 15.7 动态更新 Prompt 配置

```http
POST /api/prompt/config
Content-Type: application/json

{
  "preset": "java-engineer",
  "outputFormat": "bullet",
  "cotEnabled": true,
  "debugLog": true
}
```

**支持动态修改的字段**：enabled, debugLog, preset, systemRole, roleDescription, roleDomain, outputFormat, strictOutput, cotEnabled, cotInstruction, fewShotEnabled, boundaryEnabled, noDataReply, noFabrication, additionalConstraints, contextWindowSize, contextOnlyReply

> **注意**：动态修改仅在当前运行时生效，重启后恢复为 `application.yml` 中的值

### 15.8 重置 Prompt 配置

```http
POST /api/prompt/reset
```

**响应**：取消预设模板，恢复为 `application.yml` 中的默认配置

### 8 种预设模板说明

| 预设名 | 系统角色 | 输出格式 | CoT | Few-shot | 适用场景 |
|--------|----------|----------|-----|----------|----------|
| java-engineer | Java后端工程师 | bullet | - | - | 技术问答 |
| interviewer | 业务面试官 | text | - | - | 面试评估 |
| json-output | 数据分析助手 | json | - | - | 结构化输出 |
| cot-reasoning | 逻辑推理专家 | text | ✓ | - | 数学/逻辑推理 |
| few-shot | 智能客服助手 | text | - | ✓ | 示例驱动客服 |
| strict-boundary | 知识库问答助手 | text | - | - | 严格防幻觉 |
| table-output | 数据分析师 | table | - | - | 表格展示 |
| full-combo | 资深技术顾问 | bullet | ✓ | ✓ | 全功能组合 |

---

## 16. 文档版本管理

**Controller**: `DocumentVersionController` | **路径前缀**: `/api/document/version`

### 16.1 获取所有文档列表（当前版本）

```http
GET /api/document/version/list
```

### 16.2 获取文档所有版本

```http
GET /api/document/version/versions/{documentId}
```

### 16.3 获取文档当前版本

```http
GET /api/document/version/current/{documentId}
```

### 16.4 回退到指定版本

```http
POST /api/document/version/rollback?documentId=doc_001&targetVersion=2
```

### 16.5 删除指定版本

```http
DELETE /api/document/version/delete/{id}
```

### 16.6 比较两个版本

```http
GET /api/document/version/compare?version1Id=1&version2Id=3
```

### 16.7 检查文件是否已存在（MD5去重）

```http
GET /api/document/version/exists?fileMd5=d41d8cd98f00b204e9800998ecf8427e
```

### 16.8 获取版本详情

```http
GET /api/document/version/detail/{id}
```

---

## 17. 图片管理

**Controller**: `ImageController` | **路径前缀**: `/api/image`

### 16.1 上传图片（自动处理）

```http
POST /api/image/upload
Content-Type: multipart/form-data

file: photo.jpg
```

**响应**：包含 fileId、压缩信息、缩略图、尺寸、格式、向量化信息等

**支持格式**：JPG、PNG、GIF、WEBP、BMP  
**自动处理**：压缩、生成缩略图、EXIF 元数据提取

### 16.2 获取图片元数据

```http
GET /api/image/metadata/{fileId}
```

### 16.3 下载图片

```http
GET /api/image/download/{fileId}?thumbnail=false
```

| 参数 | 说明 |
|------|------|
| thumbnail | `true` 下载缩略图，`false`（默认）下载原图 |

### 16.4 图片格式转换

```http
POST /api/image/convert
Content-Type: multipart/form-data

file: photo.png
targetFormat: jpg
```

### 16.5 调整图片尺寸

```http
POST /api/image/resize
Content-Type: multipart/form-data

file: photo.jpg
width: 800
height: 600
keepAspectRatio: true
```

### 16.6 裁剪图片

```http
POST /api/image/crop
Content-Type: multipart/form-data

file: photo.jpg
x: 100
y: 50
width: 400
height: 300
```

### 16.7 删除图片

```http
DELETE /api/image/{fileId}
```

> 同时删除原图和缩略图

### 16.8 手动向量化图片

```http
POST /api/image/vectorize
Content-Type: application/x-www-form-urlencoded

fileId=img_001&originalFilename=photo.jpg&storagePath=/uploads/images/photo.jpg&format=jpg&width=1920&height=1080&description=风景照
```

### 16.9 图片语义搜索

```http
GET /api/image/search?query=蓝色天空&maxResults=10
```

**响应**：基于自然语言描述搜索相似图片

### 16.10 删除图片向量

```http
DELETE /api/image/vector/{vectorId}
```

---

## 18. 图片增强功能

**Controller**: `ImageEnhanceController` | **路径前缀**: `/api/image/enhance`

### 17.1 对象存储管理

```http
POST   /api/image/enhance/storage/upload      # 上传到对象存储（支持本地/MinIO/OSS/COS）
GET    /api/image/enhance/storage/policy/{type}  # 获取指定存储策略
GET    /api/image/enhance/storage/cdn-url     # 获取CDN加速URL
```

### 17.2 批量上传处理

```http
POST   /api/image/enhance/batch/upload        # 批量上传图片（并发处理+进度追踪）
GET    /api/image/enhance/batch/progress/{taskId}  # 查询批量处理进度
```

### 17.3 OCR 文字识别

```http
POST   /api/image/enhance/ocr                  # 图片OCR文字识别
```

### 17.4 AI 内容审核

```http
POST   /api/image/enhance/moderate            # 图片AI内容安全审核
```

### 17.5 智能标签

```http
POST   /api/image/enhance/tag/generate        # 自动生成图片智能标签
GET    /api/image/enhance/tag/suggestions     # 获取预定义标签建议
```

### 17.6 去重检测

```http
POST   /api/image/enhance/dedup/check         # 检查图片是否重复（感知哈希pHash/dHash）
POST   /api/image/enhance/dedup/similar       # 查找相似图片
```

### 17.7 版本管理

```http
POST   /api/image/enhance/version/create      # 创建新版本
POST   /api/image/enhance/version/switch      # 切换到指定版本
POST   /api/image/enhance/version/rollback    # 回退到指定版本
GET    /api/image/enhance/version/list/{fileId}  # 获取版本历史
GET    /api/image/enhance/version/compare     # 比较版本差异
```

### 17.8 访问统计

```http
GET    /api/image/enhance/stats/view/{fileId}    # 记录浏览量
GET    /api/image/enhance/stats/download/{fileId} # 记录下载量
GET    /api/image/enhance/stats/hot            # 热门图片排行
GET    /api/image/enhance/stats/summary/{fileId} # 图片统计摘要
```

### 17.9 防盗链

```http
POST   /api/image/enhance/hotlink/token        # 生成访问Token
POST   /api/image/enhance/hotlink/url          # 生成签名URL
GET    /api/image/enhance/hotlink/whitelist    # 获取Referer白名单
POST   /api/image/enhance/hotlink/whitelist/add  # 添加白名单
```

### 17.10 水印

```http
POST   /api/image/enhance/watermark/add        # 添加水印（支持位置、透明度配置）
```

---

## 19. 音频管理

**Controller**: `AudioController` | **路径前缀**: `/api/audio`

### 18.1 上传音频（自动处理）

```http
POST /api/audio/upload
Content-Type: multipart/form-data

file: recording.mp3
```

**支持格式**：MP3、WAV、AAC、FLAC、OGG、WMA、M4A  
**自动处理**：压缩、元数据提取（比特率/采样率/声道/艺术家/专辑等）、波形图生成

### 18.2 获取音频元数据

```http
GET /api/audio/metadata/{fileId}
```

### 18.3 下载音频

```http
GET /api/audio/download/{fileId}
```

### 18.4 音频格式转换

```http
POST /api/audio/convert
Content-Type: multipart/form-data

file: recording.wav
targetFormat: mp3
```

### 18.5 调整音频比特率

```http
POST /api/audio/adjust-bitrate
Content-Type: multipart/form-data

file: recording.mp3
bitrate: 128
```

### 18.6 获取音频时长

```http
POST /api/audio/duration
Content-Type: multipart/form-data

file: recording.mp3
```

**响应**：返回秒数和格式化时长（如 `3:45`）

### 18.7 删除音频

```http
DELETE /api/audio/{fileId}
```

> 同时删除原音频和波形图

### 18.8 手动向量化音频

```http
POST /api/audio/vectorize
Content-Type: application/x-www-form-urlencoded

fileId=audio_001&originalFilename=song.mp3&storagePath=/uploads/audios/song.mp3&format=mp3&duration=245&title=示例音频&artist=艺术家名&album=专辑名&genre=流行
```

### 18.9 转录并向量化音频

```http
POST /api/audio/transcribe-and-vectorize
Content-Type: application/x-www-form-urlencoded

fileId=audio_001&originalFilename=song.mp3&storagePath=/uploads/audios/song.mp3&format=mp3&duration=245&transcription=这是音频内容的文字转录...
```

> 更新转录文本并重新向量化，增强语义检索效果

### 18.10 音频语义搜索

```http
GET /api/audio/search?query=古典音乐&maxResults=5
```

### 18.11 删除音频向量

```http
DELETE /api/audio/vector/{vectorId}
```

---

## 20. 音频增强功能

**Controller**: `AudioEnhanceController` | **路径前缀**: `/api/audio/enhance`

### 19.1 真实波形图生成

```http
POST   /api/audio/enhance/waveform/generate        # 生成真实波形图（FFmpeg+Java Sound API，立体声分声道）
GET    /api/audio/enhance/waveform/{fileId}         # 获取波形图
```

### 19.2 批量上传

```http
POST   /api/audio/enhance/batch/upload             # 批量上传音频
GET    /api/audio/enhance/batch/progress/{taskId}   # 查询批量处理进度
```

### 19.3 语音识别 ASR

```http
POST   /api/audio/enhance/asr                      # Whisper语音识别（语音转文字）
```

### 19.4 AI 内容审核

```http
POST   /api/audio/enhance/moderate                 # 音频内容安全审核
```

### 19.5 智能标签

```http
POST   /api/audio/enhance/tag/generate             # 自动生成音频智能标签
GET    /api/audio/enhance/tag/suggestions           # 获取预定义标签建议
```

### 19.6 去重检测

```http
POST   /api/audio/enhance/dedup/check              # 音频指纹去重检测
POST   /api/audio/enhance/dedup/similar             # 查找相似音频
```

### 19.7 版本管理

```http
POST   /api/audio/enhance/version/create           # 创建新版本
POST   /api/audio/enhance/version/switch           # 切换版本
POST   /api/audio/enhance/version/rollback         # 回退版本
GET    /api/audio/enhance/version/list/{fileId}     # 版本历史
```

### 19.8 访问统计

```http
GET    /api/audio/enhance/stats/play/{fileId}       # 记录播放量
GET    /api/audio/enhance/stats/download/{fileId}    # 记录下载量
GET    /api/audio/enhance/stats/hot                # 热门音频排行
GET    /api/audio/enhance/stats/summary/{fileId}    # 音频统计摘要
```

---

## 21. 文件管理

**Controller**: `FileController` | **路径前缀**: `/api/file`

### 20.1 文件预览

```http
GET /api/file/preview?filePath=/uploads/documents/manual.pdf
```

> 支持浏览器内联展示：图片、PDF、音频、视频

### 20.2 按ID预览文件

```http
GET /api/file/preview/{storageType}/{fileId}
```

> `storageType`：`images` / `audios` / `documents` / `thumbnails`

### 20.3 获取文件信息

```http
GET /api/file/info?filePath=/uploads/documents/manual.pdf
```

**响应**：文件大小、类型、是否可预览等元数据

### 20.4 列出文件列表

```http
GET /api/file/list?dirPath=./uploads&recursive=false
```

| 参数 | 默认值 | 说明 |
|------|--------|------|
| dirPath | ./uploads | 目录路径 |
| recursive | false | 是否递归列出子目录 |

### 20.5 文件下载

```http
GET /api/file/download?filePath=/uploads/documents/manual.pdf
```

> `Content-Disposition: attachment`

### 20.6 断点续传下载

```http
GET /api/file/download/range?filePath=/uploads/videos/large.mp4
Range: bytes=0-1048575
```

> 支持 HTTP Range 请求头实现断点续传

### 20.7 批量打包下载

```http
POST /api/file/download/batch?zipFileName=export
Content-Type: application/json

[
  "/uploads/documents/file1.pdf",
  "/uploads/documents/file2.docx",
  "/uploads/images/photo.jpg"
]
```

> 将多个文件打包成 ZIP 后下载

### 20.8 上传并解压

```http
POST /api/file/decompress
Content-Type: multipart/form-data

file: archive.zip
```

**支持格式**：ZIP、7Z、TAR、TAR.GZ、BZ2、TGZ  
**安全防护**：Zip炸弹防护（限制解压大小和文件数量）、Zip Slip路径穿越攻击防护

### 20.9 解压本地已有文件

```http
POST /api/file/decompress/local?archiveFilePath=/uploads/archive.zip
```

### 20.10 查看压缩文件内容（不解压）

```http
POST /api/file/decompress/contents
Content-Type: multipart/form-data

file: archive.zip
```

### 20.11 预览解压后的文件

```http
GET /api/file/decompressed/preview/{taskId}?subPath=documents/readme.txt
```

### 20.12 列出解压后的文件

```http
GET /api/file/decompressed/list/{taskId}
```

---

## 22. 知识图谱

**Controller**: `KnowledgeGraphController` | **路径前缀**: `/api/knowledge-graph`

### 21.1 图谱构建

```http
POST /api/knowledge-graph/build
Content-Type: application/x-www-form-urlencoded

text=人工智能是计算机科学的一个分支...&documentId=doc_001&documentName=AI概述
```

> 使用 LLM 从文本中自动抽取实体和关系构建图谱

### 21.2 实体关系抽取

```http
POST /api/knowledge-graph/extract?text=腾讯公司成立于1998年，总部位于深圳
```

> 从文本中抽取实体和关系（不入库）

### 21.3 节点管理

```http
POST   /api/knowledge-graph/node                  # 创建图谱节点
GET    /api/knowledge-graph/nodes                  # 获取所有节点
GET    /api/knowledge-graph/nodes/label/{label}     # 按标签获取节点
DELETE /api/knowledge-graph/node/{nodeId}          # 删除节点（含关联关系）
```

### 21.4 关系管理

```http
POST   /api/knowledge-graph/relation               # 创建图谱关系
GET    /api/knowledge-graph/relations/{nodeId}      # 获取节点的所有关系
DELETE /api/knowledge-graph/relation/{relationId}   # 删除关系
```

### 21.5 基于图谱的问答

```http
POST /api/knowledge-graph/qa?question=腾讯公司总部在哪里？
```

> 在图谱中查找相关实体和关系，结合 LLM 生成答案

### 21.6 搜索相关节点

```http
GET /api/knowledge-graph/search?keyword=人工智能
```

### 21.7 图谱可视化数据

```http
GET /api/knowledge-graph/visualization
```

> 返回 ECharts 格式的图谱可视化数据（节点 + 关系 + 分类）

### 21.8 获取子图谱（N度邻居）

```http
GET /api/knowledge-graph/subgraph?nodeId=node_001&degree=2
```

### 21.9 图谱统计

```http
GET /api/knowledge-graph/statistics
```

> 节点数、关系数、按标签/类型分布、核心节点识别

---

## 23. 多模态知识库

**Controller**: `MultimodalKnowledgeController` | **路径前缀**: `/api/multimodal-knowledge`

### 22.1 知识入库

```http
POST /api/multimodal-knowledge/image/index   # 图片知识入库（向量化）
POST /api/multimodal-knowledge/audio/index   # 音频知识入库（向量化）
POST /api/multimodal-knowledge/video/index   # 视频知识入库（向量化）
```

**参数**：resourceId, resourcePath, title, description, analysis, keywords, tags

### 22.2 模态问答

```http
POST /api/multimodal-knowledge/image/qa    # 图片知识问答
POST /api/multimodal-knowledge/audio/qa    # 音频知识问答
POST /api/multimodal-knowledge/video/qa    # 视频知识问答
POST /api/multimodal-knowledge/mixed/qa    # 混合模态问答（图文音视频综合）
```

**参数**：
| 参数 | 说明 |
|------|------|
| question | 用户问题 |
| imageResourceId / audioResourceId / videoResourceId | 可选，指定特定资源 |

### 22.3 多模态混合检索

```http
POST /api/multimodal-knowledge/hybrid-search?query=人工智能发展&modalities=TEXT,IMAGE,AUDIO&topK=10
```

> RRF/加权/线性三种融合算法

### 22.4 多模态综合问答

```http
POST /api/multimodal-knowledge/multimodal-chat?question=推荐好看的图片&includeImages=true&includeAudios=false
```

### 22.5 跨模态语义搜索

```http
POST /api/multimodal-knowledge/cross-modal-search?query=蓝色&sourceModality=TEXT&targetModality=IMAGE&topK=10
```

### 22.6 知识条目管理

```http
GET    /api/multimodal-knowledge/list?modality=IMAGE&offset=0&limit=20   # 列表（按模态筛选）
GET    /api/multimodal-knowledge/detail/{knowledgeId}                     # 详情
DELETE /api/multimodal-knowledge/delete/{knowledgeId}                     # 删除
```

### 22.7 视频分析

```http
POST /api/multimodal-knowledge/video/analyze?videoPath=/uploads/video.mp4&frameInterval=5&maxFrames=20
```

> 提取视频关键帧并进行内容分析

### 22.8 多模态知识库统计

```http
GET /api/multimodal-knowledge/statistics
```

> 按模态统计、热门标签、入库量统计

---

## 24. 多模态检索

**Controller**: `MultimodalSearchController` | **路径前缀**: `/api/multimodal`

### 23.1 多模态混合检索

```http
GET /api/multimodal/search?query=日落风景&modalities=image,audio,text&maxResults=5
```

> 同时在图片库、音频库、文本知识库中搜索，融合排序返回结果

### 23.2 跨模态检索

```http
GET /api/multimodal/cross-search?query=古典音乐&sourceModality=text&targetModality=audio&maxResults=5
```

> 用图片描述搜索音频，或用音频描述搜索图片

### 23.3 图文混合问答

```http
GET /api/multimodal/qa?question=有什么好看的日落图片？&maxResults=10
```

> 基于图片和文本知识库的综合问答

### 23.4 多模态检索统计

```http
GET /api/multimodal/stats
```

---

## 25. 知识库增强功能

**Controller**: `KnowledgeEnhancedController` | **路径前缀**: `/api/knowledge-enhanced`

### 24.1 3D 模型知识库

```http
POST   /api/knowledge-enhanced/3d-model/import                    # 导入3D模型（OBJ/STL/GLTF/FBX）
POST   /api/knowledge-enhanced/3d-model/batch-import              # 批量导入
GET    /api/knowledge-enhanced/3d-model/search?keyword=汽车       # 搜索3D模型
GET    /api/knowledge-enhanced/3d-model/all                       # 所有3D模型
GET    /api/knowledge-enhanced/3d-model/tag/{tag}                 # 按标签筛选
GET    /api/knowledge-enhanced/3d-model/similar/{knowledgeId}     # 相似模型
GET    /api/knowledge-enhanced/3d-model/report/{knowledgeId}      # 分析报告
POST   /api/knowledge-enhanced/3d-model/compare                   # 比较两个模型
POST   /api/knowledge-enhanced/3d-model/qa                        # 3D模型知识问答
GET    /api/knowledge-enhanced/3d-model/conversion-advice/{id}    # 格式转换建议
```

### 24.2 Neo4j 图数据库集成

```http
GET    /api/knowledge-enhanced/neo4j/status                # 连接状态检查
POST   /api/knowledge-enhanced/neo4j/cypher                # 执行Cypher查询
POST   /api/knowledge-enhanced/neo4j/sync                  # 全量同步到Neo4j
GET    /api/knowledge-enhanced/neo4j/shortest-path         # 最短路径查询
GET    /api/knowledge-enhanced/neo4j/k-hop/{nodeId}        # K度邻居查询
GET    /api/knowledge-enhanced/neo4j/communities           # 社区检测
GET    /api/knowledge-enhanced/neo4j/pagerank              # PageRank中心性
GET    /api/knowledge-enhanced/neo4j/bridge-nodes          # 桥接节点
GET    /api/knowledge-enhanced/neo4j/circular-deps         # 环形依赖检测
GET    /api/knowledge-enhanced/neo4j/stats                 # 图统计
```

### 24.3 图谱推理引擎

```http
GET    /api/knowledge-enhanced/reasoning/rules              # 获取所有推理规则
POST   /api/knowledge-enhanced/reasoning/execute-all        # 执行所有规则
POST   /api/knowledge-enhanced/reasoning/execute/{ruleName} # 执行指定规则
GET    /api/knowledge-enhanced/reasoning/paths              # 查找两实体间路径
GET    /api/knowledge-enhanced/reasoning/hierarchy          # 概念层级路径
GET    /api/knowledge-enhanced/reasoning/disambiguation     # 实体消歧
POST   /api/knowledge-enhanced/reasoning/merge-entities     # 合并实体
GET    /api/knowledge-enhanced/reasoning/chain              # 生成推理链
```

### 24.4 时序知识图谱

```http
POST   /api/knowledge-enhanced/temporal/relation             # 创建时序关系
GET    /api/knowledge-enhanced/temporal/snapshot             # 获取时间点快照
GET    /api/knowledge-enhanced/temporal/changes              # 两时间点变化
GET    /api/knowledge-enhanced/temporal/timeline/{nodeId}    # 实体关系变化时间线
GET    /api/knowledge-enhanced/temporal/global-timeline      # 全局时间线
GET    /api/knowledge-enhanced/temporal/evolution/{nodeId}   # 关系演化分析
GET    /api/knowledge-enhanced/temporal/predict-trend        # 预测关系趋势
GET    /api/knowledge-enhanced/temporal/validate             # 时序一致性验证
```

### 24.5 多语言知识图谱融合

```http
GET    /api/knowledge-enhanced/multilingual/equivalents          # 多语言等价实体
POST   /api/knowledge-enhanced/multilingual/link                # 跨语言实体链接
POST   /api/knowledge-enhanced/multilingual/fuse                # 融合两个语言图谱
GET    /api/knowledge-enhanced/multilingual/cross-lingual-relations  # 跨语言关联
GET    /api/knowledge-enhanced/multilingual/language-distribution    # 语言分布
POST   /api/knowledge-enhanced/multilingual/tag-language        # 实体语言标签
POST   /api/knowledge-enhanced/multilingual/aliases             # 多语言别名
```

### 24.6 Graph Embedding

```http
POST   /api/knowledge-enhanced/embedding/generate              # 生成所有节点嵌入
GET    /api/knowledge-enhanced/embedding/predict-link          # 链接预测
GET    /api/knowledge-enhanced/embedding/missing-links         # 预测缺失链接
GET    /api/knowledge-enhanced/embedding/similar-nodes/{nodeId} # 相似节点
POST   /api/knowledge-enhanced/embedding/cluster               # 节点聚类
GET    /api/knowledge-enhanced/embedding/node/{nodeId}         # 节点嵌入向量
GET    /api/knowledge-enhanced/embedding/export                # 导出嵌入
```

### 24.7 GraphRAG（图谱增强RAG）

```http
POST   /api/knowledge-enhanced/graphrag/qa                        # GraphRAG问答
POST   /api/knowledge-enhanced/graphrag/entity-enhanced-retrieval  # 实体增强检索
POST   /api/knowledge-enhanced/graphrag/guided-retrieval           # 图谱引导检索
POST   /api/knowledge-enhanced/graphrag/enhanced-ranking           # 图谱增强排序融合
GET    /api/knowledge-enhanced/graphrag/stats                      # GraphRAG统计
```

### 24.8 图谱演化

```http
POST   /api/knowledge-enhanced/evolution/incremental-update      # 增量更新图谱
POST   /api/knowledge-enhanced/evolution/recalculate-core        # 重算核心节点
POST   /api/knowledge-enhanced/evolution/cleanup-relations       # 清理低置信度关系
POST   /api/knowledge-enhanced/evolution/cleanup-isolated        # 清理孤立节点
GET    /api/knowledge-enhanced/evolution/health                  # 图谱健康度
GET    /api/knowledge-enhanced/evolution/log                     # 演化日志
GET    /api/knowledge-enhanced/evolution/trend                   # 演化趋势
POST   /api/knowledge-enhanced/evolution/trigger-full            # 手动触发全量演化
```

---

## 26. Feign 服务间调用接口

### 25.1 AiAgentFeign

```java
@FeignClient("ai-cs-agent")
POST /ai/chat/send           // AI 对话
```

### 25.2 BaseServiceFeign

```java
@FeignClient(value = "ai-cs-base-service", fallback = BaseServiceFeignFallback.class)
GET  /customer/{id}          // 根据ID获取客户
GET  /user/info              // 获取用户信息
```

### 25.3 KnowledgeFeign

```java
@FeignClient(value = "ai-cs-knowledge", fallback = KnowledgeFeignFallback.class)
POST /knowledge/search       // 知识库检索
GET  /knowledge/faq/{id}     // 根据ID获取FAQ
```

### 25.4 WorkOrderFeign

```java
@FeignClient("ai-cs-workorder")
POST /workorder/create       // 创建工单
```

> 所有 Feign 接口均有 Fallback 降级实现

---

## 27. WebSocket 实时通信

**服务端口**: `9005`

### 26.1 连接 WebSocket

```javascript
const ws = new WebSocket('ws://localhost:9005/ws/chat?token=<jwt_token>&sessionId=session_123');

ws.onopen = function() {
  console.log('WebSocket 连接已建立');
};

ws.onmessage = function(event) {
  const message = JSON.parse(event.data);
  console.log('收到消息:', message);
};
```

### 26.2 发送消息

```javascript
ws.send(JSON.stringify({
  type: 'chat_message',
  content: '你好，我需要帮助',
  timestamp: new Date().toISOString()
}));
```

### 26.3 消息类型

| 类型 | 说明 |
|------|------|
| `system` | 系统消息（连接成功等） |
| `chat_message` | 聊天消息 |
| `typing_indicator` | 打字指示器 |

### 26.4 功能特性

- 实时消息推送
- 在线客服聊天
- 消息广播
- AI 自动回复集成
- 心跳检测与断线重连

---

## 28. 错误码说明

### HTTP 状态码

| 状态码 | 说明 |
|--------|------|
| 200 | 请求成功 |
| 400 | 请求参数错误 |
| 401 | 未授权（Token 无效或过期） |
| 403 | 禁止访问（权限不足） |
| 404 | 资源不存在 |
| 429 | 请求频率超限 |
| 500 | 服务器内部错误 |
| 503 | 服务暂时不可用 |

### 业务错误码

| 错误码 | 说明 |
|--------|------|
| 10001 | 参数验证失败 |
| 10002 | Token 无效 |
| 10003 | Token 已过期 |
| 10004 | 权限不足 |
| 20001 | 客户不存在 |
| 20002 | 会话不存在 |
| 30001 | FAQ 不存在 |
| 30002 | 向量搜索失败 |
| 40001 | 工单不存在 |
| 40002 | 工单状态不允许此操作 |
| 50001 | LLM 调用失败 |
| 50002 | 知识库检索失败 |

### 统一错误响应

```json
{
  "code": 40001,
  "message": "工单不存在",
  "data": null
}
```

---

## API 概览统计

| 类别 | 接口数量 | 所属服务 |
|------|----------|----------|
| 认证与授权 | 4 | base-service |
| 用户/角色/菜单管理 | 18 | base-service |
| 客户管理 | 2 | base-service |
| 系统配置 | 4 | base-service |
| 操作日志 | 1 | base-service |
| 数据统计 | 5 | base-service |
| AI Agent 对话 | 7 | ai-cs-agent |
| 工单管理 | 11 | ai-cs-workorder |
| 工单流程（Flowable） | 7 | ai-cs-workorder |
| 知识库 FAQ | 8 | ai-cs-knowledge |
| RAG 检索增强生成 | 7 | ai-cs-knowledge |
| Prompt 调试（六大策略） | 8 | ai-cs-knowledge |
| 文档版本管理 | 8 | ai-cs-knowledge |
| 图片管理 | 10 | ai-cs-knowledge |
| 图片增强 | 30+ | ai-cs-knowledge |
| 音频管理 | 11 | ai-cs-knowledge |
| 音频增强 | 20+ | ai-cs-knowledge |
| 文件管理 | 12 | ai-cs-knowledge |
| 知识图谱 | 16 | ai-cs-knowledge |
| 多模态知识库 | 17 | ai-cs-knowledge |
| 多模态检索 | 4 | ai-cs-knowledge |
| 知识库增强 | 40+ | ai-cs-knowledge |
| Feign 接口 | 6 | ai-cs-api |
| WebSocket | 1 | ai-cs-websocket |
| **合计** | **258+** | - |

---

## 在线文档

各微服务还提供 Knife4j/Swagger 在线 API 文档：

| 服务 | 访问地址 |
|------|----------|
| Base Service | `http://localhost:9001/doc.html` |
| AI Agent | `http://localhost:9002/doc.html` |
| Knowledge | `http://localhost:9003/doc.html` |
| WorkOrder | `http://localhost:9004/doc.html` |

---

## 相关文档

- [系统架构概览](../architecture/overview.md)
- [模块详细说明](../architecture/modules.md)
- [RAG 使用指南](../../ai-cs-knowledge/RAG_USAGE_GUIDE.md)
- [功能开发清单](../功能开发清单.md)
- [开发指南](../development/guide.md)
