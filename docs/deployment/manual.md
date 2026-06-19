# 手动部署指南

本文档介绍如何在服务器上手动部署 AI 智能客服系统（不使用容器化）。

## 📋 目录

- [服务器准备](#服务器准备)
- [环境安装](#环境安装)
- [后端服务部署](#后端服务部署)
- [前端应用部署](#前端应用部署)
- [Nginx 配置](#nginx-配置)
- [进程管理](#进程管理)
- [监控与维护](#监控与维护)

---

## 服务器准备

### 推荐配置

| 组件 | 最低配置 | 推荐配置 |
|------|---------|---------|
| CPU | 4 核 | 8 核+ |
| 内存 | 8 GB | 16 GB+ |
| 磁盘 | 100 GB SSD | 500 GB SSD+ |
| 网络 | 100 Mbps | 1 Gbps |
| 操作系统 | CentOS 7 / Ubuntu 20.04 | Ubuntu 22.04 LTS |

### 服务器清单

生产环境建议的服务器分配：

| 服务器 | 用途 | 数量 |
|--------|------|------|
| App Server 1 | 后端微服务 | 2-3 台 |
| DB Server | MySQL + Redis | 1-2 台（主从） |
| Vector DB Server | Milvus | 1 台 |
| Frontend Server | Nginx + 前端 | 1-2 台 |
| Load Balancer | Nginx/HAProxy | 1-2 台（可选） |

---

## 环境安装

### 1. 安装 JDK 17

**Ubuntu/Debian**:

```bash
# 添加 Adoptium 仓库
wget -qO - https://packages.adoptium.net/artifactory/api/gpg/key/public | sudo tee /etc/apt/keyrings/adoptium.asc
echo "deb [signed-by=/etc/apt/keyrings/adoptium.asc] https://packages.adoptium.net/artifactory/deb $(awk -F= '/^VERSION_CODENAME/{print$2}' /etc/os-release) main" | sudo tee /etc/apt/sources.list.d/adoptium.list

# 安装 JDK 17
sudo apt update
sudo apt install temurin-17-jdk

# 验证安装
java -version
```

**CentOS/RHEL**:

```bash
# 安装 JDK 17
sudo yum install java-17-openjdk-devel

# 设置默认 Java
sudo alternatives --config java

# 验证安装
java -version
```

### 2. 安装 Maven

```bash
# 下载 Maven
cd /opt
sudo wget https://downloads.apache.org/maven/maven-3/3.9.6/binaries/apache-maven-3.9.6-bin.tar.gz

# 解压
sudo tar -xzf apache-maven-3.9.6-bin.tar.gz
sudo ln -s apache-maven-3.9.6 maven

# 配置环境变量
sudo tee /etc/profile.d/maven.sh << EOF
export M2_HOME=/opt/maven
export PATH=\${M2_HOME}/bin:\${PATH}
EOF

# 加载配置
source /etc/profile.d/maven.sh

# 验证安装
mvn -version
```

### 3. 安装 MySQL 8.0

**Ubuntu**:

```bash
# 下载 MySQL APT 仓库配置
wget https://dev.mysql.com/get/mysql-apt-config_0.8.29-1_all.deb
sudo dpkg -i mysql-apt-config_0.8.29-1_all.deb

# 安装 MySQL
sudo apt update
sudo apt install mysql-server

# 启动 MySQL
sudo systemctl start mysql
sudo systemctl enable mysql

# 安全初始化
sudo mysql_secure_installation
```

**CentOS**:

```bash
# 添加 MySQL 仓库
sudo rpm -Uvh https://dev.mysql.com/get/mysql80-community-release-el7-9.noarch.rpm

# 安装 MySQL
sudo yum install mysql-server

# 启动 MySQL
sudo systemctl start mysqld
sudo systemctl enable mysqld

# 获取临时密码
sudo grep 'temporary password' /var/log/mysqld.log

# 安全初始化
sudo mysql_secure_installation
```

**创建数据库和用户**：

```bash
mysql -u root -p

CREATE DATABASE ai_cs DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'ai_cs_user'@'%' IDENTIFIED BY 'your_secure_password';
GRANT ALL PRIVILEGES ON ai_cs.* TO 'ai_cs_user'@'%';
FLUSH PRIVILEGES;
EXIT;
```

**导入初始数据**：

```bash
mysql -u ai_cs_user -p ai_cs < docs/database/init.sql
```

### 4. 安装 Redis

**Ubuntu**:

```bash
sudo apt install redis-server
sudo systemctl start redis
sudo systemctl enable redis
```

**CentOS**:

```bash
sudo yum install redis
sudo systemctl start redis
sudo systemctl enable redis
```

**配置 Redis**：

编辑 `/etc/redis/redis.conf`：

```conf
# 绑定地址（生产环境不要绑定 0.0.0.0）
bind 127.0.0.1

# 设置密码
requirepass your_redis_password

# 启用持久化
appendonly yes
```

重启 Redis：

```bash
sudo systemctl restart redis
```

### 5. 安装 Milvus（可选，用于知识库功能）

参考 [Milvus 官方文档](https://milvus.io/docs/install_standalone-docker.md) 使用 Docker 安装：

```bash
# 下载 Milvus Docker Compose 文件
curl -sfL https://github.com/milvus-io/milvus/releases/download/v2.3.0/milvus-standalone-docker-compose.yml -o docker-compose.yml

# 启动 Milvus
docker compose up -d
```

### 6. 安装 Node.js（前端构建需要）

```bash
# 使用 nvm 安装
curl -o- https://raw.githubusercontent.com/nvm-sh/nvm/v0.39.0/install.sh | bash

source ~/.bashrc

nvm install 18
nvm use 18
nvm alias default 18

# 验证安装
node -v
npm -v
```

### 7. 安装 Nginx

**Ubuntu**:

```bash
sudo apt install nginx
sudo systemctl start nginx
sudo systemctl enable nginx
```

**CentOS**:

```bash
sudo yum install nginx
sudo systemctl start nginx
sudo systemctl enable nginx
```

---

## 后端服务部署

### 1. 编译项目

在开发机器上编译：

```bash
# 克隆代码
git clone https://gitlab.com/WongHui/ai-cs-parent.git
cd ai-cs-parent

# 编译打包
mvn clean package -DskipTests
```

### 2. 上传 JAR 包到服务器

```bash
# 使用 scp 上传
scp target/ai-cs-gateway-1.0.0.jar user@server:/opt/ai-cs/gateway/
scp target/ai-cs-base-service-1.0.0.jar user@server:/opt/ai-cs/base-service/
scp target/ai-cs-ai-agent-1.0.0.jar user@server:/opt/ai-cs/ai-agent/
scp target/ai-cs-knowledge-1.0.0.jar user@server:/opt/ai-cs/knowledge/
scp target/ai-cs-workorder-1.0.0.jar user@server:/opt/ai-cs/workorder/
scp target/ai-cs-websocket-1.0.0.jar user@server:/opt/ai-cs/websocket/
```

或使用 rsync：

```bash
rsync -avz target/*.jar user@server:/opt/ai-cs/
```

### 3. 配置文件

在每台服务器上创建配置文件目录：

```bash
mkdir -p /opt/ai-cs/gateway/config
mkdir -p /opt/ai-cs/base-service/config
# ... 其他服务类似
```

创建 `application.yml` 配置文件（以 gateway 为例）：

```yaml
# /opt/ai-cs/gateway/config/application.yml
server:
  port: 8080

spring:
  application:
    name: ai-cs-gateway
  
  cloud:
    gateway:
      routes:
        - id: base-service
          uri: http://localhost:9001
          predicates:
            - Path=/api/base/**
          filters:
            - StripPrefix=2
        
        - id: ai-agent
          uri: http://localhost:9002
          predicates:
            - Path=/api/agent/**
          filters:
            - StripPrefix=2

logging:
  level:
    com.ai.cs: INFO
  file:
    name: /var/log/ai-cs/gateway.log
```

### 4. 创建启动脚本

为每个服务创建启动脚本：

**gateway-start.sh**:

```bash
#!/bin/bash

APP_NAME="ai-cs-gateway"
APP_JAR="/opt/ai-cs/gateway/ai-cs-gateway-1.0.0.jar"
APP_CONFIG="/opt/ai-cs/gateway/config/application.yml"
LOG_DIR="/var/log/ai-cs"
PID_FILE="/var/run/${APP_NAME}.pid"

# JVM 参数
JVM_OPTS="-Xms512m -Xmx1024m \
          -XX:+UseG1GC \
          -XX:MaxGCPauseMillis=200 \
          -XX:+HeapDumpOnOutOfMemoryError \
          -XX:HeapDumpPath=${LOG_DIR}/heapdump.hprof"

# 创建日志目录
mkdir -p ${LOG_DIR}

# 检查是否已运行
if [ -f ${PID_FILE} ]; then
    PID=$(cat ${PID_FILE})
    if ps -p ${PID} > /dev/null; then
        echo "${APP_NAME} is already running (PID: ${PID})"
        exit 1
    fi
fi

# 启动应用
nohup java ${JVM_OPTS} \
    -jar ${APP_JAR} \
    --spring.config.location=file:${APP_CONFIG} \
    > ${LOG_DIR}/${APP_NAME}.out 2>&1 &

echo $! > ${PID_FILE}
echo "${APP_NAME} started (PID: $!)"
```

**gateway-stop.sh**:

```bash
#!/bin/bash

APP_NAME="ai-cs-gateway"
PID_FILE="/var/run/${APP_NAME}.pid"

if [ ! -f ${PID_FILE} ]; then
    echo "${APP_NAME} is not running"
    exit 1
fi

PID=$(cat ${PID_FILE})

if ps -p ${PID} > /dev/null; then
    echo "Stopping ${APP_NAME} (PID: ${PID})..."
    kill ${PID}
    
    # 等待进程结束
    for i in {1..30}; do
        if ! ps -p ${PID} > /dev/null; then
            echo "${APP_NAME} stopped"
            rm -f ${PID_FILE}
            exit 0
        fi
        sleep 1
    done
    
    # 强制终止
    echo "Force stopping ${APP_NAME}..."
    kill -9 ${PID}
    rm -f ${PID_FILE}
else
    echo "${APP_NAME} is not running"
    rm -f ${PID_FILE}
fi
```

赋予执行权限：

```bash
chmod +x /opt/ai-cs/gateway/gateway-*.sh
```

### 5. 配置 systemd 服务（推荐）

创建 systemd 服务文件：

**/etc/systemd/system/ai-cs-gateway.service**:

```ini
[Unit]
Description=AI Customer Service Gateway
After=network.target mysql.service redis.service

[Service]
Type=simple
User=ai-cs
Group=ai-cs
WorkingDirectory=/opt/ai-cs/gateway
ExecStart=/usr/bin/java -Xms512m -Xmx1024m -jar /opt/ai-cs/gateway/ai-cs-gateway-1.0.0.jar --spring.config.location=file:/opt/ai-cs/gateway/config/application.yml
ExecStop=/bin/kill -15 $MAINPID
Restart=on-failure
RestartSec=10

StandardOutput=append:/var/log/ai-cs/gateway.log
StandardError=append:/var/log/ai-cs/gateway-error.log

[Install]
WantedBy=multi-user.target
```

重新加载 systemd 并启动服务：

```bash
sudo systemctl daemon-reload
sudo systemctl enable ai-cs-gateway
sudo systemctl start ai-cs-gateway

# 查看状态
sudo systemctl status ai-cs-gateway

# 查看日志
sudo journalctl -u ai-cs-gateway -f
```

为其他服务创建类似的 systemd 配置文件。

### 6. 启动顺序

按照以下顺序启动服务：

```bash
# 1. 基础服务
sudo systemctl start ai-cs-base-service

# 2. AI 代理服务
sudo systemctl start ai-cs-ai-agent

# 3. 知识库服务
sudo systemctl start ai-cs-knowledge

# 4. 工单服务
sudo systemctl start ai-cs-workorder

# 5. WebSocket 服务
sudo systemctl start ai-cs-websocket

# 6. API 网关（最后启动）
sudo systemctl start ai-cs-gateway
```

---

## 前端应用部署

### 1. 构建前端

在开发机器上构建：

```bash
cd ai-cs-frontend

# 安装依赖
npm install

# 构建生产版本
npm run build
```

构建产物在 `dist` 目录。

### 2. 上传到服务器

```bash
# 压缩 dist 目录
tar -czf frontend-dist.tar.gz dist/

# 上传到服务器
scp frontend-dist.tar.gz user@server:/opt/ai-cs/frontend/

# 在服务器上解压
ssh user@server
cd /opt/ai-cs/frontend
tar -xzf frontend-dist.tar.gz
```

### 3. 配置 Nginx

创建 Nginx 配置文件：

**/etc/nginx/sites-available/ai-cs**:

```nginx
server {
    listen 80;
    server_name your-domain.com;

    # 前端静态文件
    root /opt/ai-cs/frontend/dist;
    index index.html;

    # Gzip 压缩
    gzip on;
    gzip_types text/plain text/css application/json application/javascript text/xml application/xml application/xml+rss text/javascript;
    gzip_min_length 1000;

    # 前端路由支持（Vue Router history 模式）
    location / {
        try_files $uri $uri/ /index.html;
    }

    # API 代理到网关
    location /api/ {
        proxy_pass http://localhost:8080/;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        
        # 超时设置
        proxy_connect_timeout 60s;
        proxy_send_timeout 60s;
        proxy_read_timeout 60s;
    }

    # WebSocket 代理
    location /ws/ {
        proxy_pass http://localhost:9005/;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        
        # WebSocket 超时设置
        proxy_connect_timeout 7d;
        proxy_send_timeout 7d;
        proxy_read_timeout 7d;
    }

    # 静态资源缓存
    location ~* \.(js|css|png|jpg|jpeg|gif|ico|svg|woff|woff2|ttf|eot)$ {
        expires 1y;
        add_header Cache-Control "public, immutable";
    }

    # 安全头
    add_header X-Frame-Options "SAMEORIGIN" always;
    add_header X-Content-Type-Options "nosniff" always;
    add_header X-XSS-Protection "1; mode=block" always;

    # 日志
    access_log /var/log/nginx/ai-cs-access.log;
    error_log /var/log/nginx/ai-cs-error.log;
}
```

启用配置：

```bash
# 创建符号链接
sudo ln -s /etc/nginx/sites-available/ai-cs /etc/nginx/sites-enabled/

# 测试配置
sudo nginx -t

# 重启 Nginx
sudo systemctl restart nginx
```

### 4. 配置 HTTPS（推荐）

使用 Let's Encrypt 免费证书：

```bash
# 安装 Certbot
sudo apt install certbot python3-certbot-nginx

# 获取证书
sudo certbot --nginx -d your-domain.com

# 自动续期
sudo certbot renew --dry-run
```

---

## 进程管理

### 使用 Supervisor（备选方案）

如果不使用 systemd，可以使用 Supervisor 管理进程。

**安装 Supervisor**：

```bash
sudo apt install supervisor
```

**创建配置文件**：

**/etc/supervisor/conf.d/ai-cs-gateway.conf**:

```ini
[program:ai-cs-gateway]
command=/usr/bin/java -Xms512m -Xmx1024m -jar /opt/ai-cs/gateway/ai-cs-gateway-1.0.0.jar --spring.config.location=file:/opt/ai-cs/gateway/config/application.yml
directory=/opt/ai-cs/gateway
user=ai-cs
autorestart=true
redirect_stderr=true
stdout_logfile=/var/log/ai-cs/gateway.log
environment=JAVA_HOME="/usr/lib/jvm/java-17-openjdk"
```

**管理命令**：

```bash
# 重新加载配置
sudo supervisorctl reread
sudo supervisorctl update

# 启动服务
sudo supervisorctl start ai-cs-gateway

# 查看状态
sudo supervisorctl status

# 查看日志
sudo tail -f /var/log/ai-cs/gateway.log
```

---

## 监控与维护

### 1. 日志管理

**日志轮转配置**：

创建 `/etc/logrotate.d/ai-cs`：

```
/var/log/ai-cs/*.log {
    daily
    rotate 30
    compress
    delaycompress
    missingok
    notifempty
    create 0640 ai-cs ai-cs
    sharedscripts
    postrotate
        systemctl reload ai-cs-gateway > /dev/null 2>&1 || true
    endscript
}
```

### 2. 健康检查

**创建健康检查脚本**：

**/opt/ai-cs/health-check.sh**:

```bash
#!/bin/bash

SERVICES=(
    "http://localhost:8080/actuator/health:Gateway"
    "http://localhost:9001/actuator/health:Base Service"
    "http://localhost:9002/actuator/health:AI Agent"
    "http://localhost:9003/actuator/health:Knowledge"
    "http://localhost:9004/actuator/health:WorkOrder"
    "http://localhost:9005/actuator/health:WebSocket"
)

for service in "${SERVICES[@]}"; do
    IFS=':' read -r url name <<< "$service"
    response=$(curl -s -o /dev/null -w "%{http_code}" "$url")
    
    if [ "$response" = "200" ]; then
        echo "✓ $name is healthy"
    else
        echo "✗ $name is unhealthy (HTTP $response)"
        # 可以在此添加告警通知
    fi
done
```

**定时执行**：

```bash
# 添加到 crontab
crontab -e

# 每 5 分钟检查一次
*/5 * * * * /opt/ai-cs/health-check.sh >> /var/log/ai-cs/health-check.log 2>&1
```

### 3. 备份策略

**数据库备份**：

```bash
#!/bin/bash
# /opt/ai-cs/backup-db.sh

BACKUP_DIR="/backup/mysql"
DATE=$(date +%Y%m%d_%H%M%S)
DB_NAME="ai_cs"
DB_USER="ai_cs_user"
DB_PASS="your_password"

mkdir -p ${BACKUP_DIR}

mysqldump -u ${DB_USER} -p${DB_PASS} \
    --single-transaction \
    --routines \
    --triggers \
    ${DB_NAME} | gzip > ${BACKUP_DIR}/${DB_NAME}_${DATE}.sql.gz

# 删除 30 天前的备份
find ${BACKUP_DIR} -name "*.sql.gz" -mtime +30 -delete

echo "Backup completed: ${DB_NAME}_${DATE}.sql.gz"
```

**定时备份**：

```bash
# 每天凌晨 2 点备份
0 2 * * * /opt/ai-cs/backup-db.sh >> /var/log/ai-cs/backup.log 2>&1
```

### 4. 性能监控

**监控系统资源**：

```bash
# 安装监控工具
sudo apt install htop iotop nethogs

# 查看 CPU 和内存
htop

# 查看磁盘 I/O
iotop

# 查看网络流量
nethogs
```

**使用 Prometheus + Grafana**（高级）：

在 Spring Boot 应用中启用 Actuator metrics：

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus
  metrics:
    export:
      prometheus:
        enabled: true
```

---

## 故障排查

### 常见问题

#### 1. 服务无法启动

**检查步骤**：

```bash
# 查看日志
sudo journalctl -u ai-cs-gateway -n 100

# 检查端口占用
sudo netstat -tlnp | grep 8080

# 检查 Java 版本
java -version

# 检查配置文件
cat /opt/ai-cs/gateway/config/application.yml
```

#### 2. 数据库连接失败

```bash
# 测试数据库连接
mysql -h localhost -u ai_cs_user -p ai_cs

# 检查 MySQL 状态
sudo systemctl status mysql

# 检查防火墙
sudo ufw status
```

#### 3. 内存不足

```bash
# 查看内存使用
free -h

# 调整 JVM 堆大小
# 编辑 systemd 服务文件，修改 -Xms 和 -Xmx 参数
```

#### 4. 磁盘空间不足

```bash
# 查看磁盘使用
df -h

# 清理日志
sudo journalctl --vacuum-time=7d

# 清理旧备份
find /backup -type f -mtime +30 -delete
```

---

## 下一步

- 阅读 [配置管理指南](configuration.md) 了解详细配置项
- 阅读 [Docker 部署指南](docker.md) 了解容器化部署
- 查看 [数据库迁移指南](../database/migration.md) 了解数据库维护

---

**需要帮助？** 提交 [Issue](https://gitlab.com/WongHui/ai-cs-parent/-/issues) 或联系维护者。
