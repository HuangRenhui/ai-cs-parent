# Docker 部署指南

本文档介绍如何使用 Docker 容器化部署 AI 智能客服系统。

## 📋 目录

- [前置要求](#前置要求)
- [Docker 镜像构建](#docker-镜像构建)
- [Docker Compose 部署](#docker-compose-部署)
- [Kubernetes 部署](#kubernetes-部署)
- [常见问题](#常见问题)

---

## 前置要求

### 必需软件

| 软件 | 版本要求 |
|------|---------|
| Docker | 20.10+ |
| Docker Compose | 2.0+ |
| Kubernetes | 1.24+ (可选) |

### 验证安装

```bash
# 检查 Docker 版本
docker --version

# 检查 Docker Compose 版本
docker compose version
```

---

## Docker 镜像构建

### 1. 后端服务镜像

每个微服务都包含 Dockerfile，用于构建容器镜像。

**示例：ai-cs-gateway 的 Dockerfile**

```dockerfile
# 使用 JDK 17 基础镜像
FROM eclipse-temurin:17-jre-alpine

# 设置工作目录
WORKDIR /app

# 复制 JAR 包
COPY target/ai-cs-gateway-1.0.0.jar app.jar

# 暴露端口
EXPOSE 8080

# 启动应用
ENTRYPOINT ["java", "-jar", "app.jar"]
```

**构建镜像**：

```bash
# 编译项目
mvn clean package -DskipTests

# 构建 Docker 镜像
cd ai-cs-gateway
docker build -t ai-cs-gateway:latest .

# 查看镜像
docker images | grep ai-cs
```

### 2. 前端服务镜像

**ai-cs-frontend 的 Dockerfile**

```dockerfile
# 构建阶段
FROM node:18-alpine AS builder

WORKDIR /app
COPY package*.json ./
RUN npm install

COPY . .
RUN npm run build

# 生产阶段
FROM nginx:alpine

COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
```

**构建前端镜像**：

```bash
cd ai-cs-frontend
docker build -t ai-cs-frontend:latest .
```

### 3. 镜像标签管理

推荐使用语义化版本标签：

```bash
# 开发版本
docker tag ai-cs-gateway:latest ai-cs-gateway:dev-20240620

# 发布版本
docker tag ai-cs-gateway:latest ai-cs-gateway:v1.0.0

# 推送到镜像仓库
docker push your-registry.com/ai-cs-gateway:v1.0.0
```

---

## Docker Compose 部署

### 1. 创建 docker-compose.yml

在项目根目录创建 `docker-compose.yml`：

```yaml
version: '3.8'

services:
  # MySQL 数据库
  mysql:
    image: mysql:8.0
    container_name: ai-cs-mysql
    environment:
      MYSQL_ROOT_PASSWORD: root_password
      MYSQL_DATABASE: ai_cs
      MYSQL_USER: ai_cs_user
      MYSQL_PASSWORD: ai_cs_password
    ports:
      - "3306:3306"
    volumes:
      - mysql_data:/var/lib/mysql
      - ./docs/database/init.sql:/docker-entrypoint-initdb.d/init.sql
    networks:
      - ai-cs-network
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost"]
      interval: 10s
      timeout: 5s
      retries: 5

  # Redis 缓存
  redis:
    image: redis:7-alpine
    container_name: ai-cs-redis
    ports:
      - "6379:6379"
    volumes:
      - redis_data:/data
    networks:
      - ai-cs-network
    command: redis-server --appendonly yes

  # Milvus 向量数据库
  milvus:
    image: milvusdb/milvus:v2.3.0
    container_name: ai-cs-milvus
    ports:
      - "19530:19530"
      - "9091:9091"
    volumes:
      - milvus_data:/var/lib/milvus
    networks:
      - ai-cs-network
    environment:
      ETCD_ENDPOINTS: etcd:2379
      MINIO_ADDRESS: minio:9000
    depends_on:
      - etcd
      - minio

  etcd:
    image: quay.io/coreos/etcd:v3.5.5
    container_name: ai-cs-etcd
    environment:
      ETCD_AUTO_COMPACTION_MODE: revision
      ETCD_AUTO_COMPACTION_RETENTION: "1000"
      ETCD_QUOTA_BACKEND_BYTES: "4294967296"
    volumes:
      - etcd_data:/etcd
    networks:
      - ai-cs-network
    command: etcd -advertise-client-urls=http://127.0.0.1:2379 -listen-client-urls http://0.0.0.0:2379 --data-dir /etcd

  minio:
    image: minio/minio:RELEASE.2023-03-20T20-16-18Z
    container_name: ai-cs-minio
    environment:
      MINIO_ACCESS_KEY: minioadmin
      MINIO_SECRET_KEY: minioadmin
    volumes:
      - minio_data:/data
    networks:
      - ai-cs-network
    command: minio server /data --console-address ":9001"

  # API 网关
  gateway:
    image: ai-cs-gateway:latest
    container_name: ai-cs-gateway
    ports:
      - "8080:8080"
    environment:
      SPRING_PROFILES_ACTIVE: docker
      SPRING_DATASOURCE_URL: jdbc:mysql://mysql:3306/ai_cs?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
      SPRING_REDIS_HOST: redis
    networks:
      - ai-cs-network
    depends_on:
      mysql:
        condition: service_healthy
      redis:
        condition: service_started
    restart: unless-stopped

  # 基础服务
  base-service:
    image: ai-cs-base-service:latest
    container_name: ai-cs-base-service
    ports:
      - "9001:9001"
    environment:
      SPRING_PROFILES_ACTIVE: docker
      SPRING_DATASOURCE_URL: jdbc:mysql://mysql:3306/ai_cs?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
      SPRING_REDIS_HOST: redis
    networks:
      - ai-cs-network
    depends_on:
      - mysql
      - redis
    restart: unless-stopped

  # AI 代理服务
  ai-agent:
    image: ai-cs-ai-agent:latest
    container_name: ai-cs-ai-agent
    ports:
      - "9002:9002"
    environment:
      SPRING_PROFILES_ACTIVE: docker
      LLM_API_KEY: ${LLM_API_KEY}
      LLM_BASE_URL: ${LLM_BASE_URL}
    networks:
      - ai-cs-network
    depends_on:
      - gateway
    restart: unless-stopped

  # 知识库服务
  knowledge:
    image: ai-cs-knowledge:latest
    container_name: ai-cs-knowledge
    ports:
      - "9003:9003"
    environment:
      SPRING_PROFILES_ACTIVE: docker
      MILVUS_HOST: milvus
      MILVUS_PORT: 19530
    networks:
      - ai-cs-network
    depends_on:
      - milvus
    restart: unless-stopped

  # 工单服务
  workorder:
    image: ai-cs-workorder:latest
    container_name: ai-cs-workorder
    ports:
      - "9004:9004"
    environment:
      SPRING_PROFILES_ACTIVE: docker
      SPRING_DATASOURCE_URL: jdbc:mysql://mysql:3306/ai_cs?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
    networks:
      - ai-cs-network
    depends_on:
      - mysql
    restart: unless-stopped

  # WebSocket 服务
  websocket:
    image: ai-cs-websocket:latest
    container_name: ai-cs-websocket
    ports:
      - "9005:9005"
    environment:
      SPRING_PROFILES_ACTIVE: docker
      SPRING_REDIS_HOST: redis
    networks:
      - ai-cs-network
    depends_on:
      - redis
    restart: unless-stopped

  # 前端应用
  frontend:
    image: ai-cs-frontend:latest
    container_name: ai-cs-frontend
    ports:
      - "80:80"
    networks:
      - ai-cs-network
    depends_on:
      - gateway
    restart: unless-stopped

volumes:
  mysql_data:
  redis_data:
  milvus_data:
  etcd_data:
  minio_data:

networks:
  ai-cs-network:
    driver: bridge
```

### 2. 创建环境变量文件

创建 `.env` 文件：

```bash
# LLM API 配置
LLM_API_KEY=your_api_key_here
LLM_BASE_URL=https://api.example.com

# 数据库密码
MYSQL_ROOT_PASSWORD=secure_root_password
MYSQL_PASSWORD=secure_db_password
```

### 3. 启动服务

```bash
# 构建所有镜像
docker compose build

# 启动所有服务
docker compose up -d

# 查看运行状态
docker compose ps

# 查看日志
docker compose logs -f

# 查看特定服务日志
docker compose logs -f gateway
```

### 4. 停止服务

```bash
# 停止所有服务
docker compose down

# 停止并删除数据卷（谨慎使用）
docker compose down -v
```

### 5. 更新服务

```bash
# 重新构建并重启
docker compose up -d --build

# 仅重启特定服务
docker compose restart gateway
```

---

## Kubernetes 部署

### 1. 创建命名空间

```yaml
# k8s/namespace.yaml
apiVersion: v1
kind: Namespace
metadata:
  name: ai-cs
```

### 2. 配置 ConfigMap

```yaml
# k8s/configmap.yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: ai-cs-config
  namespace: ai-cs
data:
  application.yml: |
    spring:
      datasource:
        url: jdbc:mysql://mysql:3306/ai_cs?useUnicode=true&characterEncoding=utf8
      redis:
        host: redis
    milvus:
      host: milvus
      port: 19530
```

### 3. 部署示例（Gateway）

```yaml
# k8s/gateway-deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: gateway
  namespace: ai-cs
spec:
  replicas: 2
  selector:
    matchLabels:
      app: gateway
  template:
    metadata:
      labels:
        app: gateway
    spec:
      containers:
        - name: gateway
          image: ai-cs-gateway:v1.0.0
          ports:
            - containerPort: 8080
          envFrom:
            - configMapRef:
                name: ai-cs-config
          resources:
            requests:
              memory: "512Mi"
              cpu: "500m"
            limits:
              memory: "1Gi"
              cpu: "1000m"
          livenessProbe:
            httpGet:
              path: /actuator/health
              port: 8080
            initialDelaySeconds: 60
            periodSeconds: 10
          readinessProbe:
            httpGet:
              path: /actuator/health
              port: 8080
            initialDelaySeconds: 30
            periodSeconds: 5
---
apiVersion: v1
kind: Service
metadata:
  name: gateway-service
  namespace: ai-cs
spec:
  selector:
    app: gateway
  ports:
    - protocol: TCP
      port: 8080
      targetPort: 8080
  type: ClusterIP
```

### 4. 部署到 Kubernetes

```bash
# 应用配置
kubectl apply -f k8s/namespace.yaml
kubectl apply -f k8s/configmap.yaml
kubectl apply -f k8s/gateway-deployment.yaml

# 查看部署状态
kubectl get pods -n ai-cs
kubectl get services -n ai-cs

# 查看日志
kubectl logs -f deployment/gateway -n ai-cs
```

---

## 常见问题

### 1. 镜像构建失败

**问题**: Maven 编译失败或依赖下载超时

**解决方案**:

```bash
# 清理 Maven 缓存
mvn clean

# 使用国内镜像源
mvn clean package -DskipTests -s settings.xml
```

### 2. 容器启动失败

**问题**: 容器启动后立即退出

**排查步骤**:

```bash
# 查看容器日志
docker logs ai-cs-gateway

# 进入容器调试
docker exec -it ai-cs-gateway sh

# 检查配置文件
docker exec ai-cs-gateway cat /app/application.yml
```

### 3. 网络连接问题

**问题**: 服务之间无法通信

**解决方案**:

```bash
# 检查网络
docker network ls
docker network inspect ai-cs-parent_ai-cs-network

# 测试连通性
docker exec ai-cs-gateway ping mysql
```

### 4. 资源不足

**问题**: 容器因内存不足被杀死

**解决方案**:

在 `docker-compose.yml` 中限制资源：

```yaml
deploy:
  resources:
    limits:
      memory: 1G
      cpus: '1.0'
    reservations:
      memory: 512M
```

### 5. 数据持久化

**建议**:

- 使用 Docker Volumes 持久化数据库数据
- 定期备份重要数据
- 生产环境使用外部存储（如 NFS、云存储）

```bash
# 备份 MySQL 数据
docker exec ai-cs-mysql mysqldump -u root -p ai_cs > backup.sql

# 恢复数据
docker exec -i ai-cs-mysql mysql -u root -p ai_cs < backup.sql
```

---

## 监控与日志

### 日志收集

推荐使用 ELK Stack 或 Loki 收集日志：

```yaml
# 添加日志驱动
services:
  gateway:
    logging:
      driver: "json-file"
      options:
        max-size: "10m"
        max-file: "3"
```

### 健康检查

所有服务都提供了 Actuator 健康检查端点：

```bash
# 检查服务健康状态
curl http://localhost:8080/actuator/health
```

---

## 下一步

- 阅读 [配置管理指南](configuration.md) 了解详细配置项
- 阅读 [手动部署指南](manual.md) 了解传统部署方式
- 查看 [数据库迁移指南](../database/migration.md) 了解数据库维护

---

**需要帮助？** 提交 [Issue](https://gitlab.com/WongHui/ai-cs-parent/-/issues) 或联系维护者。
