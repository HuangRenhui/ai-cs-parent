# RAG 检索增强生成系统

本文档详细介绍 AI 智能客服系统中的 RAG（Retrieval-Augmented Generation，检索增强生成）功能。

## 📋 概述

RAG 系统基于 **LangChain4j + Ollama + Chroma/Milvus** 技术栈构建，为企业提供完全私有化的智能知识库问答解决方案。

### 核心特性

✅ **多格式文档支持** - PDF、TXT、DOCX、MD 等格式自动解析  
✅ **智能文本切片** - 可配置的递归切片策略（默认 500 字符/片，重叠 80 字符）  
✅ **向量化存储** - Chroma（本地开发）或 Milvus（生产环境）  
✅ **两阶段检索** - 向量召回 + Rerank 重排，提升检索精度  
✅ **多用户隔离** - 基于 userId 的会话隔离，Redis 持久化存储  
✅ **防幻觉机制** - 自定义 Prompt 模板，限制 AI 仅基于知识库回答  
✅ **多轮对话** - 滑动窗口记忆（默认 10 轮），支持连续追问  

---

## 🏗️ 架构设计

### 技术栈

| 组件 | 版本 | 用途 |
|------|------|------|
| LangChain4j | 0.32.0 | Java 版 LLM 应用框架 |
| Ollama | latest | 本地大模型服务 |
| - qwen:7b | - | 对话生成模型 |
| - nomic-embed-text | - | 向量嵌入模型 |
| - bge-reranker:latest | - | 重排模型 |
| Chroma | latest | 向量数据库（本地开发推荐） |
| Milvus | 2.x | 向量数据库（生产环境推荐） |
| Redis | 6.0+ | 对话记忆持久化 |
| Apache Tika/PdfBox | - | 文档解析 |

### 核心组件

```
ai-cs-knowledge/
├── config/                          # 配置类
│   ├── RagProperties.java          # RAG 配置属性
│   ├── UploadProperties.java       # 文件上传配置
│   ├── RedisConfig.java            # Redis 配置
│   ├── RedisChatMemoryStore.java   # Redis 对话记忆存储
│   └── LangChainConfig.java        # LangChain 核心配置
├── common/                          # 通用类
│   └── Result.java                 # 统一返回结果
├── controller/                      # 控制器
│   ├── RagController.java          # RAG 接口控制器
│   └── FaqController.java          # 传统 FAQ 控制器
├── service/                         # 服务层
│   ├── DocumentLoadService.java    # 文档加载服务
│   ├── FileUploadService.java      # 文件上传服务
│   └── RagChatService.java         # RAG 问答服务
└── util/                            # 工具类
    ├── EmbeddingUtil.java
    ├── MilvusUtil.java
    └── VectorUtil.java
```

### 数据流向

```
用户上传文档 → FileUploadService → DocumentLoadService
                                    ↓
                              文本解析与切片
                                    ↓
                              Embedding 向量化
                                    ↓
                              存入 Chroma/Milvus
                                    
用户提问 → RagChatService → 向量检索 → Rerank 重排
                                    ↓
                              组装 Prompt + 上下文
                                    ↓
                              Ollama LLM 生成答案
                                    ↓
                              返回给用户并保存记忆
```

---

## 🚀 快速开始

### 前置条件

在启动 ai-cs-knowledge 服务之前，需要确保以下服务已运行：

#### 1. Ollama 服务

```bash
# 安装 Ollama
# 访问 https://ollama.com/ 下载并安装

# 拉取所需模型
ollama pull qwen:7b
ollama pull nomic-embed-text
ollama pull bge-reranker:latest

# 验证服务
curl http://localhost:11434/api/tags
```

#### 2. Chroma 向量数据库

```bash
# 安装 chromadb
pip install chromadb

# 启动持久化向量库
chroma run --path ./chroma-db

# 验证服务
curl http://localhost:8000/api/v1/heartbeat
```

#### 3. Redis

```bash
# Windows: 下载 Redis for Windows
# Linux: sudo apt-get install redis-server

# 启动 Redis
redis-server

# 验证服务
redis-cli ping  # 应返回 PONG
```

### 配置说明

编辑 `ai-cs-knowledge/src/main/resources/application.yml`：

```yaml
rag:
  ollama:
    base-url: http://localhost:11434     # Ollama 服务地址
    llm-model: qwen:7b                   # 对话模型
    embedding-model: nomic-embed-text    # 向量模型
    rerank-model: bge-reranker:latest    # 重排模型
    temperature: 0.1                     # 温度参数（越低越严谨）
  chroma:
    base-url: http://localhost:8000      # Chroma 服务地址
    collection-name: private_knowledge_base
    persist-path: ./chroma-db            # 向量库持久化路径
  split:
    chunk-size: 500                      # 文本切片大小
    chunk-overlap: 80                    # 切片重叠字符数
  retrieve:
    top-k: 5                             # 初次检索返回数量
    rerank-top-k: 3                      # 重排后保留数量
  chat-memory:
    ttl: 604800                          # 对话记忆过期时间（秒，7天）

upload:
  temp-path: ./upload-tmp                # 临时文件目录

spring:
  redis:
    host: 127.0.0.1
    port: 6379
    database: 0
```

### 启动服务

```bash
cd ai-cs-knowledge
mvn spring-boot:run
```

访问 API 文档：http://localhost:8083/doc.html

---

## 📡 API 接口

### 1. 上传 PDF 文档入库

**接口**: `POST /api/rag/upload/pdf`

**请求参数**:
- file: PDF 文件（multipart/form-data）

**示例**:
```bash
curl -X POST "http://localhost:8083/api/rag/upload/pdf" \
  -F "file=@product-manual.pdf"
```

**响应**:
```json
{
  "code": 200,
  "msg": "success",
  "data": "文档导入成功，共分割：45 个文本片段"
}
```

**处理流程**:
1. 保存文件到临时目录（UUID 命名防止冲突）
2. 使用 Apache PdfBox 解析 PDF 内容
3. 递归文本切片（chunkSize: 500, overlap: 80）
4. 调用 Ollama Embedding 模型向量化
5. 批量存入 Chroma 向量库
6. 删除临时文件

---

### 2. 上传普通文件入库

**接口**: `POST /api/rag/upload/file`

**支持格式**: TXT、DOCX、MD

**示例**:
```bash
curl -X POST "http://localhost:8083/api/rag/upload/file" \
  -F "file=@knowledge-base.txt"
```

---

### 3. 智能问答（支持多轮对话）

**接口**: `POST /api/rag/chat`

**请求参数**:
- userId: 用户唯一 ID（字符串）
- question: 用户问题（字符串）

**示例**:
```bash
# 第一轮提问
curl -X POST "http://localhost:8083/api/rag/chat?userId=user001&question=产品的保修期是多久？"

# 第二轮追问（自动携带上一轮记忆）
curl -X POST "http://localhost:8083/api/rag/chat?userId=user001&question=那电池呢？"

# 不同用户的对话完全隔离
curl -X POST "http://localhost:8083/api/rag/chat?userId=user002&question=产品价格是多少？"
```

**响应**:
```json
{
  "code": 200,
  "msg": "success",
  "data": "根据产品手册，该产品的保修期为 2 年，从购买之日起计算。"
}
```

**特性**:
- 自动携带该用户的历史对话记忆（最多 10 轮）
- 不同用户的对话完全隔离
- Redis 持久化存储，重启服务不丢失
- TTL 过期策略（默认 7 天）

---

### 4. 清空单个用户记忆

**接口**: `POST /api/rag/memory/clear/user`

**请求参数**:
- userId: 用户 ID

**示例**:
```bash
curl -X POST "http://localhost:8083/api/rag/memory/clear/user?userId=user001"
```

---

### 5. 清空全部用户记忆

**接口**: `POST /api/rag/memory/clear/all`

**示例**:
```bash
curl -X POST "http://localhost:8083/api/rag/memory/clear/all"
```

---

## 🔧 高级配置

### 切换到 Milvus 向量库

生产环境建议使用 Milvus 替代 Chroma：

**1. 修改 pom.xml**:
```xml
<!-- 移除 Chroma 依赖 -->
<!-- 添加 Milvus 依赖 -->
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-milvus</artifactId>
    <version>${langchain4j.version}</version>
</dependency>
```

**2. 修改 application.yml**:
```yaml
rag:
  milvus:
    host: localhost
    port: 19530
    collection-name: knowledge_base
```

**3. 修改 LangChainConfig.java**:
```java
@Bean
public EmbeddingStore<?> embeddingStore() {
    return MilvusEmbeddingStore.builder()
            .host(ragProperties.getMilvus().getHost())
            .port(ragProperties.getMilvus().getPort())
            .collectionName(ragProperties.getMilvus().getCollectionName())
            .build();
}
```

### 优化检索精度

**调整切片参数**:
```yaml
rag:
  split:
    chunk-size: 300      # 减小切片大小，提高精度
    chunk-overlap: 50    # 调整重叠比例
```

**增加检索数量**:
```yaml
rag:
  retrieve:
    top-k: 10            # 初次检索返回更多候选
    rerank-top-k: 5      # 重排后保留更多
```

**更换更强大的模型**:
```yaml
rag:
  ollama:
    llm-model: qwen:14b           # 更大的模型
    embedding-model: bge-large    # 更强的嵌入模型
```

### 性能优化建议

1. **向量库优化**
   - 生产环境使用 Milvus 分布式集群
   - 建立合适的索引策略（HNSW、IVF_FLAT 等）
   - 定期清理无用向量数据

2. **缓存策略**
   - 热点问答结果缓存到 Redis
   - 设置合理的 TTL

3. **并发控制**
   - 限制单用户上传文件大小
   - 文档向量化异步处理（@Async）
   - 使用消息队列削峰

4. **模型优化**
   - 根据硬件条件选择合适大小的模型
   - 启用 GPU 加速推理
   - 量化模型降低显存占用

---

## 📊 使用场景

### 场景 1: 产品知识库问答

**背景**: 企业有大量的产品手册、技术文档、FAQ

**实施步骤**:
1. 上传所有产品文档（PDF/TXT/DOCX）
2. 用户通过自然语言提问产品信息
3. AI 基于文档准确回答，避免编造

**优势**:
- 减少客服工作量
- 7x24 小时在线
- 回答准确一致

---

### 场景 2: 内部知识管理

**背景**: 公司内部的技术文档、操作手册、规章制度

**实施步骤**:
1. 按部门/类别上传文档
2. 员工通过对话方式查询信息
3. 支持多轮追问，深入理解

**优势**:
- 提高信息检索效率
- 降低培训成本
- 知识沉淀与传承

---

### 场景 3: 智能客服辅助

**背景**: 客服中心需要快速准确地回答客户问题

**实施步骤**:
1. 导入历史工单、解决方案
2. 客服输入客户问题
3. AI 提供参考答案和依据

**优势**:
- 缩短响应时间
- 提高服务质量
- 新人快速上手

---

## ❓ 常见问题

### Q1: 如何提高检索精度？

**A**: 
1. 调整 chunkSize 和 chunkOverlap 参数
2. 增加 retrieve.top-k 数量
3. 启用 Rerank 重排（已默认开启）
4. 优化 Prompt 模板
5. 使用更强大的 Embedding 模型

### Q2: 如何实现用户认证？

**A**: 
在 Controller 层添加拦截器或过滤器，从 Token 中提取 userId 传入 chat 方法即可。

```java
@PostMapping("/chat")
public Result<String> chat(@RequestHeader("Authorization") String token,
                           @RequestParam String question) {
    String userId = JwtUtil.getUserId(token);
    String answer = ragChatService.chat(userId, question);
    return Result.success(answer);
}
```

### Q3: 如何处理大规模文档？

**A**:
1. 使用 Milvus 分布式集群
2. 文档分类管理，建立多个 Collection
3. 异步处理文档入库（@Async）
4. 增量更新，避免全量重建

### Q4: 如何监控 RAG 系统性能？

**A**:
1. 添加 Prometheus + Grafana 监控
2. 记录关键指标：
   - 文档入库耗时
   - 检索耗时
   - LLM 生成耗时
   - 检索准确率（人工标注）
3. 设置告警阈值

### Q5: Chroma 和 Milvus 如何选择？

**A**:
- **Chroma**: 适合本地开发、小规模数据、简单易用
- **Milvus**: 适合生产环境、大规模数据、分布式扩展

---

## 🔮 未来规划

### 短期（1-2 周）
- [ ] 添加单元测试和集成测试
- [ ] 实现异步文档处理（@Async）
- [ ] 添加文档处理进度查询接口
- [ ] 优化错误码和错误信息国际化

### 中期（1 个月）
- [ ] 接入 Milvus 替代 Chroma（生产级）
- [ ] 实现文档版本管理
- [ ] 添加检索结果溯源功能
- [ ] 支持流式响应（SSE）

### 长期（3 个月+）
- [ ] 知识库分类和标签管理
- [ ] 权限控制和审计日志
- [ ] 对话分析和质量监控
- [ ] 多租户支持
- [ ] 接入更多文档格式（Excel、PPT 等）

---

## 📚 相关资源

- [LangChain4j 官方文档](https://docs.langchain4j.dev/)
- [Ollama 官方文档](https://ollama.com/docs)
- [Chroma 官方文档](https://docs.trychroma.com/)
- [Milvus 官方文档](https://milvus.io/docs)
- [RAG 技术论文](https://arxiv.org/abs/2005.11401)

---

## 📝 更新记录

- **2026-06-23**: 初始版本，完成 RAG 系统集成
  - 基于 LangChain4j 0.32.0
  - 支持 Ollama + Chroma/Milvus
  - 实现多用户会话隔离
  - 完整的 API 文档和使用指南

---

**需要帮助？** 查看 [快速开始指南](../QUICK_START.md) 或提交 Issue。
