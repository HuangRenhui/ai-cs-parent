# AI智能客服系统 - 生产环境部署指南

## 环境要求

| 组件 | 版本 | 说明 |
|------|------|------|
| JDK | 17+ | 编译和运行环境 |
| Maven | 3.8+ | 项目构建 |
| MySQL | 8.0+ | 业务数据库 |
| Redis | 6.0+ | 缓存和会话管理 |
| Nacos | 2.2+ | 服务注册与配置中心 |
| Ollama | latest | 本地大模型（可选） |
| Chroma | latest | 向量数据库（可选） |
| Milvus | 2.4+ | 向量数据库（可选） |
| Neo4j | 5.x | 图数据库（可选，知识图谱功能需要） |

## 部署架构

```
                    ┌─────────────┐
                    │   Nginx     │ (可选，反向代理)
                    └──────┬──────┘
                           │
                    ┌──────▼──────┐
                    │   Gateway   │ :8080
                    └──────┬──────┘
                           │
              ┌────────────┼────────────┐
              │            │            │
    ┌─────────▼──┐  ┌─────▼─────┐  ┌──▼──────────┐
    │ BaseService│  │  Agent    │  │  Knowledge  │
    │   :9001    │  │  :9002    │  │   :9003     │
    └────────────┘  └───────────┘  └─────────────┘
              │            │            │
    ┌─────────▼──┐  ┌─────▼─────┐  ┌──▼──────────┐
    │ WorkOrder  │  │ WebSocket │  │    Job      │
    │   :9004    │  │  :9005    │  │   :8086     │
    └────────────┘  └───────────┘  └─────────────┘
              │            │            │
              └────────────┼────────────┘
                           │
              ┌────────────┼────────────┐
              │            │            │
        ┌─────▼──┐  ┌──────▼───┐  ┌───▼──────┐
        │ MySQL  │  │  Redis   │  │  Nacos   │
        └────────┘  └──────────┘  └──────────┘
```

## 部署步骤

### 1. 基础环境准备

```bash
# 安装 JDK 17
# Ubuntu/Debian
sudo apt install openjdk-17-jdk

# CentOS/RHEL
sudo yum install java-17-openjdk-devel

# 验证
java -version
```

### 2. 数据库初始化

```bash
# 登录 MySQL
mysql -u root -p

# 创建数据库
CREATE DATABASE IF NOT EXISTS ai_customer_service
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

# 导入初始化脚本
mysql -u root -p ai_customer_service < docs/database/init.sql
mysql -u root -p ai_customer_service < docs/database/init_security.sql

# 导入知识增强表（可选）
mysql -u root -p ai_customer_service < ai-cs-knowledge/src/main/resources/db/migration/add_knowledge_enhance_tables.sql
```

### 3. 启动 Nacos

```bash
# 下载并启动 Nacos
wget https://github.com/alibaba/nacos/releases/download/2.2.3/nacos-server-2.2.3.zip
unzip nacos-server-2.2.3.zip
cd nacos/bin

# Linux/Mac
sh startup.sh -m standalone

# Windows
startup.cmd -m standalone
```

### 4. 启动 Redis

```bash
# Docker 方式（推荐）
docker run -d --name redis -p 6379:6379 redis:7-alpine

# 或使用系统包管理器
# Ubuntu: sudo apt install redis-server
# CentOS: sudo yum install redis
```

### 5. 配置应用

修改各模块 `application.yml` 中的数据库连接信息：

```yaml
spring:
  datasource:
    url: jdbc:mysql://你的MySQL地址:3306/ai_customer_service?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai
    username: 你的数据库用户名
    password: 你的数据库密码
  redis:
    host: 你的Redis地址
    port: 6379
    password: 你的Redis密码
  cloud:
    nacos:
      discovery:
        server-addr: 你的Nacos地址:8848
```

### 6. 编译构建

```bash
# 在项目根目录执行
mvn clean package -DskipTests

# 编译产物在各自模块的 target/ 目录下
```

### 7. 启动服务

**推荐启动顺序**：Nacos → MySQL/Redis → 基础服务 → 业务服务 → 前端

```bash
# 1. 基础服务 (端口 9001)
java -jar ai-cs-base-service/target/ai-cs-base-service-1.0.0.jar

# 2. AI智能体服务 (端口 9002)
java -jar ai-cs-agent/target/ai-cs-agent-1.0.0.jar

# 3. 知识库服务 (端口 9003)
java -jar ai-cs-knowledge/target/ai-cs-knowledge-1.0.0.jar

# 4. 工单服务 (端口 9004)
java -jar ai-cs-workorder/target/ai-cs-workorder-1.0.0.jar

# 5. WebSocket服务 (端口 9005)
java -jar ai-cs-websocket/target/ai-cs-websocket-1.0.0.jar

# 6. 定时任务服务 (端口 8086)
java -jar ai-cs-job/target/ai-cs-job-1.0.0.jar

# 7. 网关服务 (端口 8080) - 最后启动
java -jar ai-cs-gateway/target/ai-cs-gateway-1.0.0.jar
```

### 8. 前端部署

```bash
cd ai-cs-frontend

# 安装依赖
npm install

# 开发模式
npm run dev

# 生产构建
npm run build

# 构建产物在 dist/ 目录，部署到 Nginx
cp -r dist/* /usr/share/nginx/html/
```

#### Nginx 配置示例

```nginx
server {
    listen 80;
    server_name your-domain.com;

    # 前端静态文件
    location / {
        root /usr/share/nginx/html;
        index index.html;
        try_files $uri $uri/ /index.html;
    }

    # API 代理到 Gateway
    location /api/ {
        proxy_pass http://localhost:8080/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }

    # WebSocket 代理
    location /ws/ {
        proxy_pass http://localhost:9005/ws/;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
    }
}
```

## Docker 部署（推荐）

### Dockerfile 示例

```dockerfile
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY target/*.jar app.jar
EXPOSE 9001
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### Docker Compose

```yaml
version: '3.8'
services:
  mysql:
    image: mysql:8.0
    environment:
      MYSQL_ROOT_PASSWORD: root123
      MYSQL_DATABASE: ai_customer_service
    ports:
      - "3306:3306"
    volumes:
      - mysql-data:/var/lib/mysql

  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"

  nacos:
    image: nacos/nacos-server:v2.2.3
    environment:
      MODE: standalone
    ports:
      - "8848:8848"
      - "9848:9848"

  base-service:
    build: ./ai-cs-base-service
    ports:
      - "9001:9001"
    depends_on:
      - mysql
      - redis
      - nacos
    environment:
      SPRING_DATASOURCE_URL: jdbc:mysql://mysql:3306/ai_customer_service
      SPRING_REDIS_HOST: redis
      SPRING_CLOUD_NACOS_DISCOVERY_SERVER-ADDR: nacos:8848

  gateway:
    build: ./ai-cs-gateway
    ports:
      - "8080:8080"
    depends_on:
      - base-service

volumes:
  mysql-data:
```

## 可选服务部署

### Ollama（本地大模型）

```bash
# 安装 Ollama
curl -fsSL https://ollama.com/install.sh | sh

# 下载模型
ollama pull qwen:7b
ollama pull nomic-embed-text
ollama pull llava:latest  # 多模态视觉模型（可选）
```

### Chroma（向量数据库）

```bash
# Docker 方式
docker run -d --name chroma -p 8000:8000 chromadb/chroma
```

### Neo4j（图数据库 - 知识图谱功能需要）

```bash
# Docker 方式
docker run -d --name neo4j \
  -p 7474:7474 -p 7687:7687 \
  -e NEO4J_AUTH=neo4j/password \
  neo4j:5-community

# 在 knowledge 模块配置中启用：
# knowledge-graph.enabled=true
```

## 健康检查

```bash
# 检查各服务健康状态
curl http://localhost:9001/actuator/health
curl http://localhost:9002/actuator/health
curl http://localhost:9003/actuator/health
curl http://localhost:9004/actuator/health
curl http://localhost:9005/actuator/health
curl http://localhost:8086/actuator/health
curl http://localhost:8080/actuator/health

# Prometheus 指标端点
curl http://localhost:9001/actuator/prometheus
```

## API 文档访问

启动后可通过以下地址访问各服务 API 文档：

| 服务 | 文档地址 |
|------|---------|
| 基础服务 | http://localhost:9001/doc.html |
| AI智能体 | http://localhost:9002/doc.html |
| 知识库 | http://localhost:9003/doc.html |
| 工单服务 | http://localhost:9004/doc.html |

## 常见问题

### Q: 启动时报数据库连接失败
- 检查 MySQL 是否启动
- 检查 `application.yml` 中数据库连接信息是否正确
- 检查数据库是否已创建并导入初始化脚本

### Q: Nacos 连接失败
- 确认 Nacos 已启动并可访问 http://localhost:8848/nacos
- 检查各服务 `application.yml` 中 Nacos 地址配置

### Q: 知识库 RAG 功能不可用
- 确认 Ollama 已启动并下载了对应模型
- 确认 Chroma 向量数据库已启动
- 检查 `ai-cs-knowledge` 的 `application.yml` 中 RAG 配置

### Q: Flowable 流程引擎不生效
- 确认 `application.yml` 中 `flowable.enabled` 设置为 `true`
- Flowable 会自动创建所需的数据库表
- 检查 BPMN 流程文件是否在 `classpath:/processes/` 目录下
