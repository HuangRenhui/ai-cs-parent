 # 开发指南

本指南将帮助你快速搭建开发环境并开始开发工作。

## 📋 目录

- [环境准备](#环境准备)
- [项目克隆](#项目克隆)
- [后端开发](#后端开发)
- [前端开发](#前端开发)
- [开发流程](#开发流程)
- [常见问题](#常见问题)

---

## 环境准备

### 必需软件

在开始之前，请确保你的开发环境中已安装以下软件：

| 软件 | 版本要求 | 下载地址 |
|------|---------|---------|
| JDK | 17+ | [Oracle JDK](https://www.oracle.com/java/technologies/downloads/) 或 [OpenJDK](https://openjdk.org/) |
| Maven | 3.6+ | [Maven](https://maven.apache.org/download.cgi) |
| MySQL | 8.0+ | [MySQL](https://dev.mysql.com/downloads/mysql/) |
| Redis | 6.0+ | [Redis](https://redis.io/download) |
| Node.js | 16+ | [Node.js](https://nodejs.org/) |
| Git | 最新 | [Git](https://git-scm.com/) |

### 可选工具

- **IDE**: IntelliJ IDEA（推荐）或 Eclipse
- **数据库工具**: Navicat、DBeaver 或 MySQL Workbench
- **API 测试**: Postman 或 Insomnia
- **Milvus**: 向量数据库（用于知识库功能）

### 验证安装

```bash
# 检查 Java 版本
java -version

# 检查 Maven 版本
mvn -version

# 检查 MySQL 版本
mysql --version

# 检查 Redis 版本
redis-server --version

# 检查 Node.js 版本
node -v

# 检查 npm 版本
npm -v
```

---

## 项目克隆

### 克隆代码仓库

```bash
# 克隆项目
git clone https://gitee.com/huangrenhui/ai-cs-parent.git

# 进入项目目录
cd ai-cs-parent
```

### 项目结构概览

```
ai-cs-parent/
├── ai-cs-common/          # 公共模块
├── ai-cs-api/             # Feign 客户端接口
├── ai-cs-gateway/         # API 网关
├── ai-cs-base-service/    # 基础服务
├── ai-cs-ai-agent/        # AI 代理服务
├── ai-cs-knowledge/       # 知识库服务
├── ai-cs-workorder/       # 工单服务
├── ai-cs-websocket/       # WebSocket 服务
├── ai-cs-job/             # 定时任务
├── ai-cs-frontend/        # 前端应用
└── docs/                  # 文档
```

---

## 后端开发

### 1. 初始化数据库

执行数据库初始化脚本：

```bash
# 登录 MySQL
mysql -u root -p

# 创建数据库
CREATE DATABASE ai_cs DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

# 使用数据库
USE ai_cs;

# 执行初始化脚本
source docs/database/init.sql;
```

或者使用命令行直接执行：

```bash
mysql -u root -p ai_cs < docs/database/init.sql
```

### 2. 配置环境变量

复制配置文件模板：

```bash
# 为每个服务复制配置模板
cp docs/config/application-template.yml ai-cs-gateway/src/main/resources/application.yml
cp docs/config/application-template.yml ai-cs-base-service/src/main/resources/application.yml
cp docs/config/application-template.yml ai-cs-ai-agent/src/main/resources/application.yml
cp docs/config/application-template.yml ai-cs-knowledge/src/main/resources/application.yml
cp docs/config/application-template.yml ai-cs-workorder/src/main/resources/application.yml
cp docs/config/application-template.yml ai-cs-websocket/src/main/resources/application.yml
```

修改各服务的配置文件，设置正确的数据库连接、Redis 地址等。

**关键配置项**：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/ai_cs?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
    username: root
    password: your_password
  
  redis:
    host: localhost
    port: 6379
    password: 

# Milvus 配置（知识库服务需要）
milvus:
  host: localhost
  port: 19530

# LLM 配置（AI 代理服务需要）
llm:
  api-key: your_api_key
  base-url: https://api.example.com
```

### 3. 编译项目

```bash
# 在项目根目录执行
mvn clean install -DskipTests
```

### 4. 启动服务

**推荐启动顺序**：

1. **ai-cs-gateway** (端口 8080) - API 网关
2. **ai-cs-base-service** (端口 9001) - 基础服务
3. **ai-cs-ai-agent** (端口 9002) - AI 代理服务
4. **ai-cs-knowledge** (端口 9003) - 知识库服务
5. **ai-cs-workorder** (端口 9004) - 工单服务
6. **ai-cs-websocket** (端口 9005) - WebSocket 服务

**启动方式**：

```bash
# 方式 1: 使用 Maven
cd ai-cs-gateway
mvn spring-boot:run

# 方式 2: 使用 IDE
# 在 IntelliJ IDEA 中打开各服务的 Application 类，点击运行按钮

# 方式 3: 使用 JAR 包
cd ai-cs-gateway/target
java -jar ai-cs-gateway-1.0.0.jar
```

### 5. 验证服务

访问以下地址验证服务是否正常启动：

- 网关: http://localhost:8080/actuator/health
- 基础服务: http://localhost:9001/actuator/health
- AI 代理: http://localhost:9002/actuator/health

---

## 前端开发

### 1. 安装依赖

```bash
cd ai-cs-frontend
npm install
```

### 2. 配置 API 地址

编辑 `ai-cs-frontend/src/utils/request.js`，确保 API 基础 URL 正确：

```javascript
const baseURL = 'http://localhost:8080/api';
```

### 3. 启动开发服务器

```bash
npm run dev
```

前端应用将在 http://localhost:5173 启动（默认端口）。

### 4. 构建生产版本

```bash
npm run build
```

构建产物将输出到 `dist` 目录。

---

## 开发流程

### 分支管理策略

我们采用 Git Flow 工作流：

- **main**: 主分支，保持稳定，用于生产部署
- **develop**: 开发分支，日常开发集成
- **feature/\***: 功能分支，从 develop 分出
- **hotfix/\***: 热修复分支，从 main 分出

```bash
# 创建功能分支
git checkout develop
git checkout -b feature/your-feature-name

# 开发完成后合并回 develop
git checkout develop
git merge feature/your-feature-name
git push origin develop
```

### 代码提交规范

遵循 [Conventional Commits](https://www.conventionalcommits.org/) 规范：

```
<type>(<scope>): <description>

[optional body]

[optional footer(s)]
```

**Type 类型**：
- `feat`: 新功能
- `fix`: 修复 bug
- `docs`: 文档变更
- `style`: 代码格式（不影响代码运行）
- `refactor`: 重构
- `test`: 测试相关
- `chore`: 构建过程或辅助工具的变动

**示例**：

```bash
git commit -m "feat(agent): 添加对话历史记忆功能"
git commit -m "fix(knowledge): 修复向量搜索精度问题"
git commit -m "docs: 更新 API 文档"
```

### 开发新功能步骤

1. **创建功能分支**
   ```bash
   git checkout develop
   git checkout -b feature/customer-management
   ```

2. **编写代码**
   - 遵循 [代码规范](standards.md)
   - 编写单元测试
   - 更新相关文档

3. **本地测试**
   ```bash
   mvn test
   ```

4. **提交代码**
   ```bash
   git add .
   git commit -m "feat(base): 添加客户管理功能"
   git push origin feature/customer-management
   ```

5. **创建 Merge Request**
   - 在 GitLab 上创建 MR
   - 指定审查者
   - 等待代码审查和合并

### 调试技巧

#### 后端调试

1. **启用日志**

在 `application.yml` 中调整日志级别：

```yaml
logging:
  level:
    com.ai.cs: DEBUG
    org.springframework.web: DEBUG
```

2. **远程调试**

```bash
java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005 -jar app.jar
```

然后在 IDE 中配置 Remote Debug。

#### 前端调试

1. **浏览器开发者工具**
   - 使用 Vue Devtools 插件
   - 查看 Network 面板的 API 请求

2. **控制台日志**
   ```javascript
   console.log('Debug info:', data);
   ```

---

## 常见问题

### 1. Maven 依赖下载失败

**问题**: 依赖下载缓慢或失败

**解决方案**:

配置阿里云 Maven 镜像，编辑 `~/.m2/settings.xml`：

```xml
<mirrors>
  <mirror>
    <id>aliyun</id>
    <mirrorOf>central</mirrorOf>
    <name>Aliyun Maven Mirror</name>
    <url>https://maven.aliyun.com/repository/public</url>
  </mirror>
</mirrors>
```

### 2. 端口被占用

**问题**: 服务启动时提示端口已被占用

**解决方案**:

```bash
# Windows: 查找占用端口的进程
netstat -ano | findstr :8080
taskkill /PID <PID> /F

# Linux/Mac: 
lsof -i :8080
kill -9 <PID>
```

或者修改配置文件中的端口号。

### 3. 数据库连接失败

**问题**: 服务启动时无法连接数据库

**检查清单**:
- MySQL 服务是否启动
- 数据库是否已创建
- 用户名密码是否正确
- 防火墙是否阻止连接

### 4. Redis 连接失败

**问题**: 无法连接 Redis

**解决方案**:

```bash
# 检查 Redis 是否运行
redis-cli ping

# 应该返回 PONG
```

### 5. 前端跨域问题

**问题**: 前端调用 API 时出现 CORS 错误

**解决方案**:

网关已配置 CORS，确保前端请求通过网关（端口 8080），而不是直接访问后端服务。

### 6. Milvus 连接失败

**问题**: 知识库服务无法连接 Milvus

**解决方案**:

确保 Milvus 已启动：

```bash
# 使用 Docker 启动 Milvus
docker-compose up -d
```

---

## 下一步

- 阅读 [代码规范](standards.md) 了解编码约定
- 阅读 [测试指南](testing.md) 学习如何编写测试
- 查看 [架构文档](../architecture/overview.md) 深入理解系统设计

---

**遇到问题？** 提交 [Issue](https://gitee.com/huangrenhui/ai-cs-parent/issues) 或联系维护者。
