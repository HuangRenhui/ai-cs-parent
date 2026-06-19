# AI 智能客服系统 (ai-cs-parent)

## 项目简介

AI 智能客服系统是一个基于 Spring Boot 3.x 构建的微服务架构项目，集成了大语言模型（LLM）、知识库检索、工单管理等功能，为企业提供智能化的客户服务解决方案。

## 技术栈

- **后端框架**: Spring Boot 3.x + Java 17
- **微服务架构**: Spring Cloud Gateway
- **数据库**: MySQL + MyBatis Plus
- **向量数据库**: Milvus
- **缓存**: Redis
- **通信协议**: WebSocket
- **AI 能力**: LLM 集成 + RAG 知识检索
- **构建工具**: Maven

## 项目结构

```
ai-cs-parent/
├── ai-cs-common/          # 公共模块 - 通用工具类、常量、DTO、异常处理
├── ai-cs-api/             # API 模块 - Feign 客户端接口定义
├── ai-cs-gateway/         # 网关模块 - 统一入口、路由转发
├── ai-cs-base-service/    # 基础服务 - 客户管理、会话管理
├── ai-cs-ai-agent/        # AI 代理模块 - LLM 对话服务
├── ai-cs-knowledge/       # 知识库模块 - FAQ 管理、向量检索
├── ai-cs-workorder/       # 工单模块 - 工单创建与管理
├── ai-cs-websocket/       # WebSocket 模块 - 实时通信
└── ai-cs-job/             # 定时任务模块
```

## 核心功能

### 1. AI 智能对话
- 基于大语言模型的智能问答
- 支持多种 LLM 模型配置
- 上下文会话管理

### 2. 知识库检索 (RAG)
- FAQ 知识库管理
- 向量相似度检索
- Embedding 向量化服务

### 3. 客户服务
- 客户信息管理
- 聊天会话管理
- 实时消息推送 (WebSocket)

### 4. 工单系统
- 智能工单创建
- 工单流转管理
- 问题跟踪与处理

## 环境要求

- JDK 17+
- Maven 3.6+
- MySQL 8.0+
- Redis 6.0+
- Milvus 2.x (可选，用于向量检索)

## 快速开始

### 1. 克隆项目

```bash
git clone https://gitlab.com/WongHui/ai-cs-parent.git
cd ai-cs-parent
```

### 2. 配置环境变量

在各模块的 `application.yml` 中配置以下关键参数：

**AI 模型配置** (`ai-cs-ai-agent`):
```yaml
llm:
  url: http://127.0.0.1:8000/v1/chat/completions
  api-key: xxx
  model: qwen-7b
  temperature: 0.2
```

**Embedding 服务配置** (`ai-cs-knowledge`):
```yaml
embedding:
  url: http://127.0.0.1:8000/embedding
```

**数据库配置** (各服务模块):
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/ai_cs_db?useUnicode=true&characterEncoding=utf8
    username: root
    password: your_password
```

### 3. 编译项目

```bash
mvn clean install
```

### 4. 启动服务

按以下顺序启动各微服务：

1. **网关服务**: `ai-cs-gateway`
2. **基础服务**: `ai-cs-base-service`
3. **AI 代理服务**: `ai-cs-ai-agent`
4. **知识库服务**: `ai-cs-knowledge`
5. **工单服务**: `ai-cs-workorder`
6. **WebSocket 服务**: `ai-cs-websocket`

```bash
# 示例：启动网关服务
cd ai-cs-gateway
mvn spring-boot:run
```

### 5. 访问服务

- **网关地址**: http://localhost:8080
- **WebSocket 连接**: ws://localhost:8081/ws

## 配置说明

### 可配置项

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `llm.url` | http://127.0.0.1:8000/v1/chat/completions | 大模型服务地址 |
| `llm.api-key` | xxx | API 密钥 |
| `llm.model` | qwen-7b | 模型名称 |
| `llm.temperature` | 0.2 | 温度参数 |
| `embedding.url` | http://127.0.0.1:8000/embedding | Embedding 服务地址 |

## 开发指南

### 代码规范

- 变量命名使用驼峰命名法 (camelCase)
- 注释使用英文，特殊业务逻辑可使用中文
- 遵循 Spring Boot 最佳实践

### 添加新模块

1. 在父 POM 中添加新模块
2. 继承 `ai-cs-common` 公共依赖
3. 配置独立的 `application.yml`
4. 在网关中配置路由规则

### 测试

```bash
# 运行所有测试
mvn test

# 运行指定模块测试
cd ai-cs-ai-agent
mvn test
```

## 部署

### Docker 部署 (推荐)

为每个微服务创建 Dockerfile，使用 docker-compose 编排：

```yaml
version: '3'
services:
  gateway:
    build: ./ai-cs-gateway
    ports:
      - "8080:8080"
  
  base-service:
    build: ./ai-cs-base-service
    depends_on:
      - mysql
      - redis
  
  # ... 其他服务
```

### 传统部署

1. 打包各模块: `mvn clean package`
2. 上传 JAR 包到服务器
3. 使用脚本启动: `java -jar xxx.jar`

## 常见问题

### 1. Maven 构建警告

使用 Java 17 时可能出现警告，这是正常现象，不影响构建结果。

### 2. 依赖冲突

确保父 POM 中的依赖管理使用 `<dependencyManagement>` 标签。

### 3. Jakarta EE 迁移

Spring Boot 3.x 已将 `javax.annotation` 改为 `jakarta.annotation`，请检查导入语句。

## 贡献指南

欢迎提交 Issue 和 Pull Request！

1. Fork 本仓库
2. 创建特性分支: `git checkout -b feature/AmazingFeature`
3. 提交更改: `git commit -m 'Add some AmazingFeature'`
4. 推送到分支: `git push origin feature/AmazingFeature`
5. 开启 Pull Request

## 许可证

本项目采用 [MIT 许可证](LICENSE)

## 联系方式

- 项目维护者: Wong Hui
- GitLab: https://gitlab.com/WongHui/ai-cs-parent

## 项目状态

🟢 持续开发中

---

**注意**: 本 README 为中文版，如需查看英文文档请参考其他分支或翻译版本。
