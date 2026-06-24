# 配置管理指南

本文档详细说明系统的配置管理策略和最佳实践。

## 📋 配置概览

### 配置文件结构

```
ai-cs-parent/
├── ai-cs-gateway/
│   └── src/main/resources/
│       ├── application.yml              # 主配置文件
│       ├── application-dev.yml          # 开发环境
│       ├── application-test.yml         # 测试环境
│       └── application-prod.yml         # 生产环境
├── ai-cs-base-service/
│   └── src/main/resources/
│       ├── application.yml
│       ├── application-dev.yml
│       ├── application-test.yml
│       └── application-prod.yml
└── ... (其他服务类似)
```

### 配置优先级

Spring Boot 配置加载优先级（从高到低）：

1. 命令行参数
2. JNDI 属性
3. Java 系统属性
4. 操作系统环境变量
5. `application-{profile}.yml` (Profile 特定配置)
6. `application.yml` (默认配置)
7. `@PropertySource` 注解
8. `SpringApplication.setDefaultProperties()`

---

## 🔧 核心配置项

### 1. 服务器配置

**适用模块**: 所有服务模块

```yaml
server:
  port: 9001                    # 服务端口
  servlet:
    context-path: /             # 上下文路径
  tomcat:
    threads:
      max: 200                  # 最大线程数
      min-spare: 10             # 最小空闲线程
    accept-count: 100           # 等待队列长度
    max-connections: 8192       # 最大连接数
```

**各服务默认端口**:

| 服务 | 端口 | 说明 |
|------|------|------|
| ai-cs-gateway | 8080 | 网关服务 |
| ai-cs-base-service | 9001 | 基础服务 |
| ai-cs-agent | 9002 | AI 代理服务 |
| ai-cs-knowledge | 9003 | 知识库服务 |
| ai-cs-workorder | 9004 | 工单服务 |
| ai-cs-websocket | 9005 | WebSocket 服务 |

---

### 2. 数据库配置

**适用模块**: ai-cs-base-service, ai-cs-knowledge, ai-cs-workorder

```yaml
spring:
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://localhost:3306/ai_cs_db?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
    username: root
    password: ${DB_PASSWORD:your_password}  # 支持环境变量覆盖
    
    # HikariCP 连接池配置
    hikari:
      minimum-idle: 5                       # 最小空闲连接数
      maximum-pool-size: 20                 # 最大连接池大小
      connection-timeout: 30000             # 连接超时时间 (ms)
      idle-timeout: 600000                  # 空闲超时时间 (ms)
      max-lifetime: 1800000                 # 连接最大生命周期 (ms)
      pool-name: AiCsHikariPool
```

**环境变量方式**:
```bash
export DB_PASSWORD=your_secure_password
java -jar ai-cs-base-service.jar
```

---

### 3. Redis 配置

**适用模块**: 需要缓存的服务

```yaml
spring:
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD:}
      database: 0
      timeout: 3000ms
      
      lettuce:
        pool:
          max-active: 8           # 最大活跃连接数
          max-idle: 8             # 最大空闲连接数
          min-idle: 0             # 最小空闲连接数
          max-wait: -1ms          # 最大等待时间
```

---

### 4. LLM 模型配置

**适用模块**: ai-cs-agent

```yaml
llm:
  url: ${LLM_URL:http://127.0.0.1:8000/v1/chat/completions}
  api-key: ${LLM_API_KEY:your-api-key}
  model: ${LLM_MODEL:qwen-7b}
  temperature: ${LLM_TEMPERATURE:0.2}
  max-tokens: ${LLM_MAX_TOKENS:2048}
  top-p: ${LLM_TOP_P:0.9}
  timeout: ${LLM_TIMEOUT:30000}
```

**配置说明**:

| 配置项 | 说明 | 推荐值 |
|--------|------|--------|
| url | LLM 服务地址 | 根据实际部署调整 |
| api-key | API 密钥 | 从服务商获取 |
| model | 模型名称 | qwen-7b, chatglm, gpt-3.5-turbo 等 |
| temperature | 温度参数 (0-1) | 0.2 (确定性高) ~ 0.8 (创造性高) |
| max-tokens | 最大 token 数 | 1024 ~ 4096 |
| top-p | 核采样参数 | 0.9 |
| timeout | 请求超时时间 (ms) | 30000 |

---

### 5. Embedding 服务配置

**适用模块**: ai-cs-knowledge

```yaml
embedding:
  url: ${EMBEDDING_URL:http://127.0.0.1:8000/embedding}
  dimension: ${EMBEDDING_DIMENSION:768}
  timeout: ${EMBEDDING_TIMEOUT:10000}
```

---

### 6. Milvus 向量数据库配置

**适用模块**: ai-cs-knowledge

```yaml
milvus:
  host: ${MILVUS_HOST:localhost}
  port: ${MILVUS_PORT:19530}
  username: ${MILVUS_USERNAME:}
  password: ${MILVUS_PASSWORD:}
  
  collection:
    name: faq_vectors
    dimension: 768
    index-type: IVF_FLAT
    metric-type: COSINE
    
  search:
    top-k: 5
    nprobe: 10
    similarity-threshold: 0.8
```

---

### 7. Gateway 路由配置

**适用模块**: ai-cs-gateway

```yaml
spring:
  cloud:
    gateway:
      routes:
        # 基础服务路由
        - id: base-service
          uri: http://localhost:9001
          predicates:
            - Path=/api/base/**
          filters:
            - StripPrefix=1
            
        # AI 代理服务路由
        - id: ai-agent
          uri: http://localhost:9002
          predicates:
            - Path=/api/ai/**
          filters:
            - StripPrefix=1
            
        # 知识库服务路由
        - id: knowledge
          uri: http://localhost:9003
          predicates:
            - Path=/api/knowledge/**
          filters:
            - StripPrefix=1
            
        # 工单服务路由
        - id: workorder
          uri: http://localhost:9004
          predicates:
            - Path=/api/workorder/**
          filters:
            - StripPrefix=1
            
        # WebSocket 路由
        - id: websocket
          uri: ws://localhost:9005
          predicates:
            - Path=/ws/**
```

---

### 8. MyBatis Plus 配置

**适用模块**: 使用数据库的服务

```yaml
mybatis-plus:
  mapper-locations: classpath*:/mapper/**/*.xml
  type-aliases-package: com.ai.cs.**.entity
  
  configuration:
    map-underscore-to-camel-case: true    # 下划线转驼峰
    cache-enabled: false                  # 禁用二级缓存
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl  # 日志实现
    
  global-config:
    db-config:
      id-type: AUTO                       # 主键策略
      logic-delete-field: deleted         # 逻辑删除字段
      logic-delete-value: 1               # 已删除
      logic-not-delete-value: 0           # 未删除
```

---

### 9. WebSocket 配置

**适用模块**: ai-cs-websocket

```yaml
websocket:
  endpoint: /ws/chat
  allowed-origins: "*"                    # 允许的跨域来源
  max-session-per-user: 10                # 每用户最大会话数
  heartbeat-interval: 30000               # 心跳间隔 (ms)
  session-timeout: 600000                 # 会话超时时间 (ms)
```

---

### 10. 日志配置

**适用模块**: 所有服务模块

```yaml
logging:
  level:
    root: INFO
    com.ai.cs: DEBUG
    org.springframework.web: INFO
    com.baomidou.mybatisplus: DEBUG
    
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n"
    file: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n"
    
  file:
    name: logs/ai-cs-service.log
    max-size: 10MB
    max-history: 30
```

---

## 🌍 多环境配置

### 开发环境 (application-dev.yml)

```yaml
spring:
  profiles: dev
  
  datasource:
    url: jdbc:mysql://localhost:3306/ai_cs_db_dev
    username: root
    password: dev_password
    
  data:
    redis:
      host: localhost
      port: 6379

logging:
  level:
    com.ai.cs: DEBUG

server:
  port: 9001
```

### 测试环境 (application-test.yml)

```yaml
spring:
  profiles: test
  
  datasource:
    url: jdbc:mysql://test-db:3306/ai_cs_db_test
    username: test_user
    password: ${TEST_DB_PASSWORD}
    
  data:
    redis:
      host: test-redis
      port: 6379
      password: ${TEST_REDIS_PASSWORD}

llm:
  url: http://test-llm:8000/v1/chat/completions
  api-key: ${TEST_LLM_API_KEY}
```

### 生产环境 (application-prod.yml)

```yaml
spring:
  profiles: prod
  
  datasource:
    url: jdbc:mysql://prod-db-master:3306/ai_cs_db?useSSL=true
    username: ${PROD_DB_USER}
    password: ${PROD_DB_PASSWORD}
    hikari:
      maximum-pool-size: 50
      
  data:
    redis:
      host: ${PROD_REDIS_HOST}
      port: 6379
      password: ${PROD_REDIS_PASSWORD}
      lettuce:
        pool:
          max-active: 20

llm:
  url: ${PROD_LLM_URL}
  api-key: ${PROD_LLM_API_KEY}
  temperature: 0.3

logging:
  level:
    root: WARN
    com.ai.cs: INFO
  file:
    name: /var/log/ai-cs/service.log
```

---

## 🔐 敏感信息管理

### 方式 1: 环境变量

```bash
# 设置环境变量
export DB_PASSWORD=secure_password
export REDIS_PASSWORD=redis_secret
export LLM_API_KEY=sk-xxxxx

# 启动应用
java -jar ai-cs-base-service.jar
```

### 方式 2: JVM 参数

```bash
java -jar ai-cs-base-service.jar \
  -Dspring.datasource.password=secure_password \
  -Dllm.api-key=sk-xxxxx
```

### 方式 3: 外部配置文件

```bash
# 创建外部配置文件
cat > /etc/ai-cs/config.yml << EOF
spring:
  datasource:
    password: secure_password
llm:
  api-key: sk-xxxxx
EOF

# 指定外部配置
java -jar ai-cs-base-service.jar --spring.config.location=file:/etc/ai-cs/config.yml
```

### 方式 4: Spring Cloud Config (推荐生产环境)

```yaml
# bootstrap.yml
spring:
  cloud:
    config:
      uri: http://config-server:8888
      profile: prod
      label: main
```

---

## 🚀 配置最佳实践

### 1. 使用 Profile 隔离环境

```bash
# 开发环境
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# 测试环境
mvn spring-boot:run -Dspring-boot.run.profiles=test

# 生产环境
java -jar app.jar --spring.profiles.active=prod
```

### 2. 配置验证

启动时验证必需配置：

```java
@Component
public class ConfigValidator implements CommandLineRunner {
    
    @Value("${llm.url}")
    private String llmUrl;
    
    @Value("${spring.datasource.url}")
    private String dbUrl;
    
    @Override
    public void run(String... args) {
        if (llmUrl == null || llmUrl.isEmpty()) {
            throw new IllegalStateException("LLM URL must be configured");
        }
        if (dbUrl == null || dbUrl.isEmpty()) {
            throw new IllegalStateException("Database URL must be configured");
        }
    }
}
```

### 3. 配置热刷新

使用 `@RefreshScope` 实现配置动态刷新：

```java
@RestController
@RefreshScope
public class ConfigController {
    
    @Value("${llm.temperature:0.2}")
    private double temperature;
    
    @GetMapping("/config/temperature")
    public double getTemperature() {
        return temperature;
    }
}
```

### 4. 配置文档化

为每个配置项添加注释：

```yaml
llm:
  # LLM 服务地址，格式: http://host:port/v1/chat/completions
  url: http://127.0.0.1:8000/v1/chat/completions
  
  # API 密钥，从 LLM 服务商获取
  api-key: ${LLM_API_KEY}
  
  # 模型名称，可选: qwen-7b, chatglm, gpt-3.5-turbo
  model: qwen-7b
  
  # 温度参数 (0-1)，值越高输出越随机
  temperature: 0.2
```

---

## 📊 配置监控

### Actuator 端点

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,env,configprops
  endpoint:
    env:
      show-values: ALWAYS
    configprops:
      show-values: ALWAYS
```

访问配置信息：
- 环境变量: http://localhost:9001/actuator/env
- 配置属性: http://localhost:9001/actuator/configprops

---

## 🔍 故障排查

### 问题 1: 配置未生效

**检查步骤**:
1. 确认激活的 Profile: `--spring.profiles.active=dev`
2. 检查配置文件位置是否正确
3. 查看启动日志中的配置加载信息
4. 使用 Actuator 端点验证配置值

### 问题 2: 敏感信息泄露

**解决方案**:
1. 不要将密码硬编码在配置文件中
2. 使用环境变量或密钥管理服务
3. `.gitignore` 中排除敏感配置文件
4. 使用加密配置 (Jasypt)

### 问题 3: 配置冲突

**排查方法**:
```bash
# 查看配置加载顺序
java -jar app.jar --debug | grep "Config data"

# 查看最终生效的配置
curl http://localhost:9001/actuator/env
```

---

## 📚 相关文档

- [快速开始指南](../QUICK_START.md)
- [Docker 部署指南](../deployment/docker.md)
- [系统架构概览](../architecture/overview.md)
