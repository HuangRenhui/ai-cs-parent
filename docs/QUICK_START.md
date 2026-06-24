# 快速开始指南

本指南将帮助您快速搭建和运行 AI 智能客服系统。

## 📋 环境要求

### 必需环境
- **JDK**: 17 或更高版本
- **Maven**: 3.6 或更高版本
- **MySQL**: 8.0 或更高版本
- **Redis**: 6.0 或更高版本
- **Node.js**: 16+ (前端开发)
- **npm**: 8+ (前端开发)

### 可选环境
- **Milvus**: 2.x (用于向量检索功能，生产环境推荐)
- **Chroma**: latest (用于向量检索功能，本地开发推荐)
- **Ollama**: latest (本地大模型服务，RAG 功能必需)
- **Docker & Docker Compose**: (容器化部署)

## 🚀 快速启动

### 第一步：克隆项目

```bash
git clone https://gitlab.com/WongHui/ai-cs-parent.git
cd ai-cs-parent
```

### 第二步：初始化数据库

1. 创建数据库：
```sql
CREATE DATABASE ai_cs_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

2. 执行初始化脚本：
```bash
mysql -u root -p ai_cs_db < docs/database/init.sql
```

### 第二步半：启动 RAG 前置服务（可选，如需使用 RAG 功能）

如果您需要使用知识库的 RAG（检索增强生成）功能，需要先启动以下服务：

#### 1. 安装并启动 Ollama

```bash
# 访问 https://ollama.com/ 下载安装

# 拉取所需模型
ollama pull qwen:7b                    # 大语言模型
ollama pull nomic-embed-text           # 向量嵌入模型
ollama pull bge-reranker:latest        # 重排模型

# Ollama 默认运行在 http://localhost:11434
```

#### 2. 启动 Chroma 向量数据库

```bash
# 安装 chromadb
pip install chromadb

# 启动持久化向量库
chroma run --path ./chroma-db

# Chroma 默认运行在 http://localhost:8000
```

#### 3. 启动 Redis

```bash
# Windows: 下载 Redis for Windows
# Linux: sudo apt-get install redis-server

# 启动 Redis（默认端口 6379）
redis-server
```

> **提示**: 如果不需要 RAG 功能，可以跳过此步骤。传统 FAQ 管理功能仍然可用。

### 第三步：配置环境变量

#### 3.1 配置 AI 模型服务（ai-cs-ai-agent）

编辑 `ai-cs-ai-agent/src/main/resources/application.yml`：

```yaml
llm:
  url: http://127.0.0.1:8000/v1/chat/completions  # LLM 服务地址
  api-key: your-api-key                            # API 密钥
  model: qwen-7b                                   # 模型名称
  temperature: 0.2                                 # 温度参数
```

#### 3.2 配置 Embedding 服务（ai-cs-knowledge）

编辑 `ai-cs-knowledge/src/main/resources/application.yml`：

**传统 Embedding 配置**（如果使用外部 Embedding 服务）：
```yaml
embedding:
  url: http://127.0.0.1:8000/embedding  # Embedding 服务地址
```

**RAG 配置**（如果使用 LangChain4j + Ollama）：
```yaml
rag:
  ollama:
    base-url: http://localhost:11434     # Ollama 服务地址
    llm-model: qwen:7b                   # 对话模型
    embedding-model: nomic-embed-text    # 向量模型
    rerank-model: bge-reranker:latest    # 重排模型
    temperature: 0.1                     # 温度参数
  chroma:
    base-url: http://localhost:8000      # Chroma 服务地址
    collection-name: private_knowledge_base
  split:
    chunk-size: 500                      # 文本切片大小
    chunk-overlap: 80                    # 切片重叠字符数
  retrieve:
    top-k: 5                             # 初次检索返回数量
    rerank-top-k: 3                      # 重排后保留数量
  chat-memory:
    ttl: 604800                          # 对话记忆过期时间（秒，7天）

spring:
  redis:
    host: 127.0.0.1
    port: 6379
```

#### 3.3 配置数据库连接（所有服务模块）

在每个服务的 `application.yml` 中配置：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/ai_cs_db?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
    username: root
    password: your_password
    driver-class-name: com.mysql.cj.jdbc.Driver
```

#### 3.4 配置 Redis（需要 Redis 的服务）

```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379
      password: your_redis_password  # 如果有密码
      database: 0
```

### 第四步：编译项目

```bash
mvn clean install -DskipTests
```

> **提示**: 首次编译可能需要较长时间下载依赖，请耐心等待。

### 第五步：启动后端服务

**按以下顺序启动各微服务**（每个服务在新终端窗口中运行）：

#### 1. 网关服务 (端口: 8080)
```bash
cd ai-cs-gateway
mvn spring-boot:run
```

#### 2. 基础服务 (端口: 9001)
```bash
cd ai-cs-base-service
mvn spring-boot:run
```

#### 3. AI 代理服务 (端口: 9002)
```bash
cd ai-cs-ai-agent
mvn spring-boot:run
```

#### 4. 知识库服务 (端口: 9003)
```bash
cd ai-cs-knowledge
mvn spring-boot:run
```

#### 5. 工单服务 (端口: 9004)
```bash
cd ai-cs-workorder
mvn spring-boot:run
```

#### 6. WebSocket 服务 (端口: 9005)
```bash
cd ai-cs-websocket
mvn spring-boot:run
```

### 第六步：启动前端服务

```bash
cd ai-cs-frontend
npm install
npm run dev
```

前端服务默认运行在 `http://localhost:5173`

## ✅ 验证部署

### 后端服务验证

访问以下地址确认服务启动成功：

- **网关健康检查**: http://localhost:8080/actuator/health
- **基础服务**: http://localhost:9001/actuator/health
- **AI 代理服务**: http://localhost:9002/actuator/health
- **知识库服务**: http://localhost:9003/actuator/health

### 前端页面验证

打开浏览器访问 `http://localhost:5173`，应该能看到：

- AI 聊天页面 (/chat)
- 客户管理页面 (/customer)
- 知识库管理页面 (/knowledge)
- 工单管理页面 (/workorder)

### 功能测试

1. **测试 AI 对话**：在聊天页面发送消息，确认能收到 AI 回复
2. **测试客户管理**：尝试添加新客户
3. **测试知识库**：添加 FAQ 并进行搜索
4. **测试工单创建**：创建一个新工单

## 🔧 常见问题

### 1. Maven 构建失败

**问题**: 依赖下载失败或版本冲突

**解决方案**:
```bash
# 清理本地仓库缓存
mvn dependency:purge-local-repository

# 重新编译
mvn clean install -U
```

### 2. 端口被占用

**问题**: 服务启动时提示端口已被占用

**解决方案**:
```bash
# Windows 查看端口占用
netstat -ano | findstr :8080

# Linux/Mac 查看端口占用
lsof -i :8080

# 杀死占用进程
taskkill /F /PID <PID>  # Windows
kill -9 <PID>           # Linux/Mac
```

### 3. 数据库连接失败

**问题**: 服务启动时无法连接 MySQL

**解决方案**:
- 确认 MySQL 服务已启动
- 检查数据库用户名和密码是否正确
- 确认数据库 `ai_cs_db` 已创建
- 检查防火墙设置

### 4. Redis 连接失败

**问题**: 服务启动时无法连接 Redis

**解决方案**:
- 确认 Redis 服务已启动
- 检查 Redis 配置中的主机和端口
- 如果设置了密码，确认密码正确

### 5. LLM 服务调用失败

**问题**: AI 对话返回错误

**解决方案**:
- 确认 LLM 服务地址配置正确
- 检查 API Key 是否有效
- 确认网络连接正常
- 查看服务日志获取详细错误信息

### 6. 前端页面空白

**问题**: 访问前端页面显示空白

**解决方案**:
- 检查浏览器控制台是否有错误
- 确认后端服务已全部启动
- 检查前端代理配置是否正确
- 清除浏览器缓存后重试

## 📝 下一步

- 📖 查看 [架构文档](architecture/overview.md) 了解系统设计
- 🛠️ 查看 [开发指南](development/guide.md) 开始二次开发
- 🚢 查看 [部署文档](deployment/docker.md) 了解生产部署
- 📊 查看 [API 文档](api/README.md) 了解接口详情

## 💡 开发建议

### 调试技巧

1. **查看日志**: 每个服务启动时会输出日志，关注 ERROR 和 WARN 级别
2. **断点调试**: 使用 IDE 的远程调试功能连接到运行中的服务
3. **Postman 测试**: 使用 Postman 直接调用后端接口进行测试
4. **浏览器开发者工具**: 前端调试时使用 Network 和 Console 面板

### 性能优化

1. **跳过测试**: 开发时使用 `-DskipTests` 加快构建速度
2. **增量编译**: 只编译修改过的模块 `mvn install -pl module-name`
3. **热重载**: 配置 Spring DevTools 实现代码修改自动重启

### 代码规范

- 遵循阿里巴巴 Java 开发手册
- 使用 Lombok 简化代码
- 统一异常处理和返回格式
- 编写必要的单元测试

---

**需要帮助？** 查看 [完整文档索引](../docs/README.md) 或提交 Issue。
