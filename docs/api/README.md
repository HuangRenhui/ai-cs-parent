# API 接口文档

本文档提供 AI 智能客服系统的 RESTful API 接口说明。

## 📋 目录

- [基础信息](#基础信息)
- [认证与授权](#认证与授权)
- [API 列表](#api-列表)
- [错误码说明](#错误码说明)
- [示例代码](#示例代码)

---

## 基础信息

### API 基础 URL

```
开发环境: http://localhost:8080/api
生产环境: https://your-domain.com/api
```

### 通用请求头

```http
Content-Type: application/json
Authorization: Bearer <token>
X-Request-ID: <unique-request-id>
```

### 响应格式

**成功响应**：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    // 响应数据
  },
  "timestamp": 1718870400000
}
```

**失败响应**：

```json
{
  "code": 400,
  "message": "参数验证失败",
  "data": null,
  "timestamp": 1718870400000,
  "errors": [
    {
      "field": "username",
      "message": "用户名不能为空"
    }
  ]
}
```

---

## 认证与授权

### JWT Token 认证

系统使用 JWT（JSON Web Token）进行身份验证。

**获取 Token**：

```http
POST /api/auth/login
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
  "message": "登录成功",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "expiresIn": 7200,
    "refreshToken": "dGhpcyBpcyBhIHJlZnJlc2ggdG9rZW4..."
  }
}
```

**使用 Token**：

在后续请求的 Header 中携带 Token：

```http
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

**刷新 Token**：

```http
POST /api/auth/refresh
Content-Type: application/json

{
  "refreshToken": "dGhpcyBpcyBhIHJlZnJlc2ggdG9rZW4..."
}
```

---

## API 列表

### 1. 客户管理 (Base Service)

#### 1.1 创建客户

```http
POST /api/base/customers
Content-Type: application/json
Authorization: Bearer <token>

{
  "name": "张三",
  "phone": "13800138000",
  "email": "zhangsan@example.com",
  "company": "某某公司"
}
```

**响应**：

```json
{
  "code": 200,
  "message": "创建成功",
  "data": {
    "id": 1,
    "name": "张三",
    "phone": "13800138000",
    "email": "zhangsan@example.com",
    "company": "某某公司",
    "createdAt": "2024-06-20T10:00:00Z"
  }
}
```

#### 1.2 查询客户列表

```http
GET /api/base/customers?page=1&size=10&keyword=张三
Authorization: Bearer <token>
```

**查询参数**：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| page | Integer | 否 | 页码，默认 1 |
| size | Integer | 否 | 每页数量，默认 10 |
| keyword | String | 否 | 搜索关键词 |

**响应**：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "total": 100,
    "page": 1,
    "size": 10,
    "items": [
      {
        "id": 1,
        "name": "张三",
        "phone": "13800138000",
        "email": "zhangsan@example.com"
      }
    ]
  }
}
```

#### 1.3 查询客户详情

```http
GET /api/base/customers/{id}
Authorization: Bearer <token>
```

**响应**：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 1,
    "name": "张三",
    "phone": "13800138000",
    "email": "zhangsan@example.com",
    "company": "某某公司",
    "chatSessions": [
      {
        "sessionId": "session_123",
        "startTime": "2024-06-20T10:00:00Z",
        "endTime": "2024-06-20T10:30:00Z",
        "messageCount": 20
      }
    ]
  }
}
```

#### 1.4 更新客户信息

```http
PUT /api/base/customers/{id}
Content-Type: application/json
Authorization: Bearer <token>

{
  "name": "张三",
  "phone": "13800138000",
  "email": "zhangsan_new@example.com"
}
```

#### 1.5 删除客户

```http
DELETE /api/base/customers/{id}
Authorization: Bearer <token>
```

---

### 2. AI 对话 (AI Agent)

#### 2.1 发送消息

```http
POST /api/agent/chat
Content-Type: application/json
Authorization: Bearer <token>

{
  "sessionId": "session_123",
  "customerId": 1,
  "message": "如何重置密码？",
  "context": {
    "previousMessages": [
      {
        "role": "user",
        "content": "你好"
      },
      {
        "role": "assistant",
        "content": "您好！有什么可以帮助您的吗？"
      }
    ]
  }
}
```

**响应**：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "messageId": "msg_456",
    "sessionId": "session_123",
    "reply": "您可以通过以下步骤重置密码：\n1. 点击登录页面的'忘记密码'\n2. 输入您的邮箱\n3. 查收邮件并点击重置链接",
    "sources": [
      {
        "faqId": 10,
        "question": "如何重置密码？",
        "similarity": 0.95
      }
    ],
    "suggestedActions": [
      {
        "type": "button",
        "label": "立即重置",
        "action": "/reset-password"
      }
    ]
  }
}
```

#### 2.2 创建会话

```http
POST /api/agent/sessions
Content-Type: application/json
Authorization: Bearer <token>

{
  "customerId": 1,
  "channel": "web"
}
```

**响应**：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "sessionId": "session_123",
    "customerId": 1,
    "startTime": "2024-06-20T10:00:00Z",
    "status": "active"
  }
}
```

#### 2.3 获取会话历史

```http
GET /api/agent/sessions/{sessionId}/messages?page=1&size=50
Authorization: Bearer <token>
```

**响应**：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "total": 20,
    "messages": [
      {
        "id": 1,
        "role": "user",
        "content": "你好",
        "timestamp": "2024-06-20T10:00:00Z"
      },
      {
        "id": 2,
        "role": "assistant",
        "content": "您好！有什么可以帮助您的吗？",
        "timestamp": "2024-06-20T10:00:01Z"
      }
    ]
  }
}
```

#### 2.4 结束会话

```http
POST /api/agent/sessions/{sessionId}/end
Authorization: Bearer <token>
```

---

### 3. 知识库 (Knowledge Base)

#### 3.1 添加 FAQ

```http
POST /api/knowledge/faqs
Content-Type: application/json
Authorization: Bearer <token>

{
  "question": "如何重置密码？",
  "answer": "您可以通过以下步骤重置密码：\n1. 点击登录页面的'忘记密码'\n2. 输入您的邮箱\n3. 查收邮件并点击重置链接",
  "category": "账户管理",
  "tags": ["密码", "重置", "账户"],
  "priority": 1
}
```

**响应**：

```json
{
  "code": 200,
  "message": "创建成功",
  "data": {
    "id": 10,
    "question": "如何重置密码？",
    "answer": "您可以通过以下步骤重置密码...",
    "category": "账户管理",
    "vectorId": "vec_abc123",
    "createdAt": "2024-06-20T10:00:00Z"
  }
}
```

#### 3.2 查询 FAQ 列表

```http
GET /api/knowledge/faqs?page=1&size=10&category=账户管理&keyword=密码
Authorization: Bearer <token>
```

**响应**：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "total": 5,
    "page": 1,
    "size": 10,
    "items": [
      {
        "id": 10,
        "question": "如何重置密码？",
        "answer": "您可以通过以下步骤...",
        "category": "账户管理",
        "viewCount": 150,
        "createdAt": "2024-06-20T10:00:00Z"
      }
    ]
  }
}
```

#### 3.3 向量搜索

```http
POST /api/knowledge/search
Content-Type: application/json
Authorization: Bearer <token>

{
  "query": "忘记密码怎么办",
  "topK": 5,
  "threshold": 0.8
}
```

**响应**：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "results": [
      {
        "faqId": 10,
        "question": "如何重置密码？",
        "answer": "您可以通过以下步骤...",
        "similarity": 0.95,
        "category": "账户管理"
      },
      {
        "faqId": 11,
        "question": "密码忘记了如何找回？",
        "answer": "请按照以下流程...",
        "similarity": 0.88,
        "category": "账户管理"
      }
    ]
  }
}
```

#### 3.4 更新 FAQ

```http
PUT /api/knowledge/faqs/{id}
Content-Type: application/json
Authorization: Bearer <token>

{
  "question": "如何重置密码？",
  "answer": "更新后的答案内容...",
  "category": "账户管理"
}
```

#### 3.5 删除 FAQ

```http
DELETE /api/knowledge/faqs/{id}
Authorization: Bearer <token>
```

---

### 4. 工单管理 (Work Order)

#### 4.1 创建工单

```http
POST /api/workorder/tickets
Content-Type: application/json
Authorization: Bearer <token>

{
  "customerId": 1,
  "title": "无法登录系统",
  "description": "用户反馈登录后提示账号异常",
  "priority": "high",
  "category": "技术问题",
  "assignedTo": "support_team_1",
  "relatedSessionId": "session_123"
}
```

**响应**：

```json
{
  "code": 200,
  "message": "创建成功",
  "data": {
    "id": 100,
    "ticketNo": "TKT-20240620-001",
    "customerId": 1,
    "title": "无法登录系统",
    "status": "open",
    "priority": "high",
    "createdAt": "2024-06-20T10:00:00Z"
  }
}
```

#### 4.2 查询工单列表

```http
GET /api/workorder/tickets?page=1&size=10&status=open&priority=high
Authorization: Bearer <token>
```

**查询参数**：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| status | String | 否 | 状态：open, in_progress, resolved, closed |
| priority | String | 否 | 优先级：low, medium, high, urgent |
| category | String | 否 | 分类 |
| assignedTo | String | 否 | 负责人 |

**响应**：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "total": 25,
    "page": 1,
    "size": 10,
    "items": [
      {
        "id": 100,
        "ticketNo": "TKT-20240620-001",
        "title": "无法登录系统",
        "status": "open",
        "priority": "high",
        "assignedTo": "support_team_1",
        "createdAt": "2024-06-20T10:00:00Z"
      }
    ]
  }
}
```

#### 4.3 查询工单详情

```http
GET /api/workorder/tickets/{id}
Authorization: Bearer <token>
```

**响应**：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 100,
    "ticketNo": "TKT-20240620-001",
    "customerId": 1,
    "customerName": "张三",
    "title": "无法登录系统",
    "description": "用户反馈登录后提示账号异常",
    "status": "in_progress",
    "priority": "high",
    "category": "技术问题",
    "assignedTo": "support_team_1",
    "comments": [
      {
        "id": 1,
        "author": "客服小王",
        "content": "已联系技术部门处理",
        "createdAt": "2024-06-20T11:00:00Z"
      }
    ],
    "createdAt": "2024-06-20T10:00:00Z",
    "updatedAt": "2024-06-20T11:00:00Z"
  }
}
```

#### 4.4 更新工单状态

```http
PATCH /api/workorder/tickets/{id}/status
Content-Type: application/json
Authorization: Bearer <token>

{
  "status": "in_progress",
  "comment": "已开始处理"
}
```

#### 4.5 添加工单评论

```http
POST /api/workorder/tickets/{id}/comments
Content-Type: application/json
Authorization: Bearer <token>

{
  "content": "问题已解决，请用户确认",
  "attachments": [
    {
      "fileName": "screenshot.png",
      "fileUrl": "https://example.com/files/screenshot.png"
    }
  ]
}
```

---

### 5. WebSocket 实时通信

#### 5.1 连接 WebSocket

```javascript
const ws = new WebSocket('ws://localhost:9005/ws/chat?token=<jwt_token>&sessionId=session_123');

ws.onopen = function() {
  console.log('WebSocket 连接已建立');
};

ws.onmessage = function(event) {
  const message = JSON.parse(event.data);
  console.log('收到消息:', message);
};

ws.onerror = function(error) {
  console.error('WebSocket 错误:', error);
};

ws.onclose = function() {
  console.log('WebSocket 连接已关闭');
};
```

#### 5.2 发送消息

```javascript
const message = {
  type: 'chat_message',
  content: '你好，我需要帮助',
  timestamp: new Date().toISOString()
};

ws.send(JSON.stringify(message));
```

#### 5.3 接收消息类型

**系统消息**：

```json
{
  "type": "system",
  "content": "连接成功",
  "timestamp": "2024-06-20T10:00:00Z"
}
```

**聊天消息**：

```json
{
  "type": "chat_message",
  "role": "assistant",
  "content": "您好！请问有什么可以帮助您的？",
  "messageId": "msg_123",
  "timestamp": "2024-06-20T10:00:01Z"
}
```

**打字指示器**：

```json
{
  "type": "typing_indicator",
  "isTyping": true,
  "timestamp": "2024-06-20T10:00:01Z"
}
```

---

## 错误码说明

### HTTP 状态码

| 状态码 | 说明 |
|--------|------|
| 200 | 请求成功 |
| 201 | 资源创建成功 |
| 204 | 删除成功 |
| 400 | 请求参数错误 |
| 401 | 未授权（Token 无效或过期） |
| 403 | 禁止访问（权限不足） |
| 404 | 资源不存在 |
| 409 | 资源冲突（如重复创建） |
| 422 | 业务逻辑错误 |
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

### 错误响应示例

```json
{
  "code": 10001,
  "message": "参数验证失败",
  "data": null,
  "timestamp": 1718870400000,
  "errors": [
    {
      "field": "email",
      "message": "邮箱格式不正确"
    },
    {
      "field": "phone",
      "message": "手机号格式不正确"
    }
  ]
}
```

---

## 示例代码

### JavaScript (Fetch API)

```javascript
// 获取客户列表
async function getCustomers(page = 1, size = 10) {
  const response = await fetch(`/api/base/customers?page=${page}&size=${size}`, {
    method: 'GET',
    headers: {
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json'
    }
  });
  
  if (!response.ok) {
    throw new Error(`HTTP error! status: ${response.status}`);
  }
  
  return await response.json();
}

// 发送 AI 对话消息
async function sendChatMessage(sessionId, message) {
  const response = await fetch('/api/agent/chat', {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({
      sessionId,
      message,
      customerId: 1
    })
  });
  
  const result = await response.json();
  
  if (result.code !== 200) {
    throw new Error(result.message);
  }
  
  return result.data;
}
```

### Python (Requests)

```python
import requests

BASE_URL = 'http://localhost:8080/api'
HEADERS = {
    'Authorization': f'Bearer {token}',
    'Content-Type': 'application/json'
}

# 创建工单
def create_ticket(customer_id, title, description, priority='medium'):
    url = f'{BASE_URL}/workorder/tickets'
    data = {
        'customerId': customer_id,
        'title': title,
        'description': description,
        'priority': priority
    }
    
    response = requests.post(url, json=data, headers=HEADERS)
    response.raise_for_status()
    
    return response.json()

# 搜索知识库
def search_knowledge(query, top_k=5):
    url = f'{BASE_URL}/knowledge/search'
    data = {
        'query': query,
        'topK': top_k,
        'threshold': 0.8
    }
    
    response = requests.post(url, json=data, headers=HEADERS)
    response.raise_for_status()
    
    return response.json()
```

### cURL

```bash
# 获取客户列表
curl -X GET "http://localhost:8080/api/base/customers?page=1&size=10" \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json"

# 创建 FAQ
curl -X POST "http://localhost:8080/api/knowledge/faqs" \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "question": "如何重置密码？",
    "answer": "您可以通过以下步骤重置密码...",
    "category": "账户管理"
  }'

# 创建工单
curl -X POST "http://localhost:8080/api/workorder/tickets" \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": 1,
    "title": "无法登录系统",
    "description": "用户反馈登录后提示账号异常",
    "priority": "high"
  }'
```

---

## API 版本管理

当前 API 版本：**v1**

API 路径中包含版本号：`/api/v1/...`（可选）

未来将在 major version 升级时引入 breaking changes。

---

## 速率限制

为防止滥用，API 实施了速率限制：

| 端点类型 | 限制 |
|---------|------|
| 普通 API | 100 请求/分钟/IP |
| 聊天 API | 30 请求/分钟/IP |
| 搜索 API | 60 请求/分钟/IP |

超出限制将返回 `429 Too Many Requests`。

---

## 下一步

- 阅读 [架构文档](../architecture/modules.md) 了解各模块职责
- 阅读 [开发指南](../development/guide.md) 开始开发
- 查看 [测试指南](../development/testing.md) 学习 API 测试

---

**需要帮助？** 提交 [Issue](https://gitee.com/huangrenhui/ai-cs-parent/issues) 或联系维护者。
