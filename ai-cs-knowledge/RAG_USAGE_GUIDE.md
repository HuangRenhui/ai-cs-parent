# RAG知识库系统使用指南

## 功能概述

本项目已完整实现基于 LangChain4j + Ollama + Chroma 的私有化RAG（检索增强生成）系统，具备以下核心功能：

### ✅ 已实现功能

1. **多用户隔离的智能问答**
   - 支持按userId隔离对话历史
   - Redis持久化存储，重启服务不丢失会话
   - 滑动窗口记忆机制（默认保留最近10轮对话）

2. **文档上传与向量化入库**
   - 支持PDF、TXT、DOCX、MD等多种格式
   - 自动文本切片、向量化并存入Chroma向量库
   - 临时文件自动清理机制

3. **高级检索优化**
   - Rerank重排模型提升检索精度
   - 自定义Prompt模板约束AI仅基于知识库回答
   - 防幻觉机制，避免编造信息

4. **会话管理**
   - 清空单个用户对话记忆
   - 清空全部用户会话
   - 对话历史TTL过期策略（默认7天）

5. **API文档**
   - Knife4j在线接口文档
   - 完整的Swagger注解
   - 可视化测试界面

---

## 前置环境准备

### 1. 安装并启动Ollama

```bash
# 访问官网下载安装
# https://ollama.com/

# 拉取所需模型
ollama pull qwen:7b                    # 大语言模型
ollama pull nomic-embed-text           # 向量嵌入模型
ollama pull bge-reranker:latest        # 重排模型
```

### 2. 启动Chroma向量数据库

```bash
# 安装chromadb
pip install chromadb

# 启动持久化向量库
chroma run --path ./chroma-db
```

### 3. 启动Redis

```bash
# Windows (需先安装Redis)
redis-server

# Linux/Mac
sudo systemctl start redis
```

### 4. 配置application.yml

确保以下配置正确：

```yaml
rag:
  ollama:
    base-url: http://localhost:11434
    llm-model: qwen:7b
    embedding-model: nomic-embed-text
    rerank-model: bge-reranker:latest
    temperature: 0.1
  chroma:
    base-url: http://localhost:8000
    collection-name: private_knowledge_base
    persist-path: ./chroma-db

spring:
  redis:
    host: 127.0.0.1
    port: 6379
```

---

## API接口说明

启动项目后访问：http://localhost:8083/doc.html

### 1. 智能问答接口

**接口地址：** `POST /api/rag/chat`

**请求参数：**
- userId: 用户唯一ID（字符串）
- question: 用户问题（字符串）

**示例：**
```bash
curl -X POST "http://localhost:8083/api/rag/chat?userId=user001&question=这份文档的核心内容是什么？"
```

**特性：**
- 自动携带该用户的历史对话记忆
- 不同用户的对话完全隔离
- 支持多轮连续追问

---

### 2. 上传PDF文档入库

**接口地址：** `POST /api/rag/upload/pdf`

**请求参数：**
- file: PDF文件（multipart/form-data）

**示例：**
```bash
curl -X POST "http://localhost:8083/api/rag/upload/pdf" \
  -F "file=@/path/to/document.pdf"
```

**处理流程：**
1. 保存文件到临时目录
2. 解析PDF内容
3. 递归切片（chunkSize: 500, overlap: 80）
4. 向量化并存入Chroma
5. 删除临时文件

---

### 3. 上传普通文件入库

**接口地址：** `POST /api/rag/upload/file`

**支持格式：** TXT、DOCX、MD等

**示例：**
```bash
curl -X POST "http://localhost:8083/api/rag/upload/file" \
  -F "file=@/path/to/document.txt"
```

---

### 4. 清空单个用户记忆

**接口地址：** `POST /api/rag/memory/clear/user`

**请求参数：**
- userId: 用户ID

**示例：**
```bash
curl -X POST "http://localhost:8083/api/rag/memory/clear/user?userId=user001"
```

---

### 5. 清空全部用户记忆

**接口地址：** `POST /api/rag/memory/clear/all`

**示例：**
```bash
curl -X POST "http://localhost:8083/api/rag/memory/clear/all"
```

---

## 架构设计

### 核心组件

1. **LangChainConfig** - LangChain4j核心配置
   - ChatLanguageModel: Ollama大语言模型
   - EmbeddingModel: 向量嵌入模型
   - RerankingModel: 重排模型
   - EmbeddingStore: Chroma向量库
   - RetrievalAugmentor: 检索增强器（带Rerank）
   - ChatMemoryProvider: 多用户记忆提供器

2. **RagChatService** - RAG聊天服务
   - 基于AiServices构建AI助手
   - 自动绑定检索增强、对话记忆
   - 支持userId级别的会话隔离

3. **DocumentLoadService** - 文档加载服务
   - 支持PDF、TXT、DOCX等格式解析
   - 递归文本切片
   - 批量向量化入库

4. **FileUploadService** - 文件上传服务
   - 临时文件管理
   - UUID防重名
   - 自动清理机制

5. **RedisChatMemoryStore** - Redis持久化记忆存储
   - 实现ChatMemoryStore接口
   - TTL过期策略
   - 支持通配符批量删除

---

## 技术栈

- **Spring Boot**: 3.2.x
- **LangChain4j**: 0.32.0
- **Ollama**: 本地大模型服务
- **Chroma**: 本地向量数据库
- **Redis**: 对话记忆持久化
- **Knife4j**: API文档

---

## 常见问题

### Q1: 如何迁移到Milvus向量库？

只需修改pom.xml依赖和LangChainConfig中的EmbeddingStore Bean：

```xml
<!-- 替换为Milvus -->
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-milvus</artifactId>
    <version>${langchain4j.version}</version>
</dependency>
```

```java
@Bean
public EmbeddingStore<?> embeddingStore() {
    return MilvusEmbeddingStore.builder()
            .host("localhost")
            .port(19530)
            .collectionName("knowledge")
            .build();
}
```

### Q2: 如何提高检索精度？

1. 调整chunkSize和chunkOverlap参数
2. 增加retrieve.top-k数量
3. 启用Rerank重排（已默认开启）
4. 优化Prompt模板

### Q3: 如何实现用户认证？

在Controller层添加拦截器或过滤器，从Token中提取userId传入chat方法即可。

---

## 性能优化建议

1. **向量库优化**
   - 生产环境使用Milvus分布式集群
   - 建立合适的索引策略
   - 定期清理无用向量数据

2. **缓存策略**
   - 热点问答结果缓存到Redis
   - 设置合理的TTL

3. **并发控制**
   - 限制单用户上传文件大小
   - 文档向量化异步处理
   - 使用消息队列削峰

4. **模型优化**
   - 根据硬件条件选择合适大小的模型
   - 启用GPU加速推理
   - 量化模型降低显存占用

---

## 下一步扩展方向

- [ ] 接入更多文档格式（Excel、PPT等）
- [ ] 实现文档版本管理
- [ ] 添加检索结果溯源功能
- [ ] 集成权限管理系统
- [ ] 支持流式响应（SSE）
- [ ] 添加对话导出功能
- [ ] 实现知识库分类管理

---

## 联系方式

如有问题请联系开发团队。
