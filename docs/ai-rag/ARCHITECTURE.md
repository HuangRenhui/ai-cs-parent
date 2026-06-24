# RAG 系统架构设计

本文档详细说明 RAG（检索增强生成）系统的架构设计、核心组件和技术实现。

## 🏗️ 整体架构

```
┌─────────────────────────────────────────────────────────────┐
│                      用户层 (Frontend)                       │
└──────────────────────┬──────────────────────────────────────┘
                       │ HTTP Request
┌──────────────────────▼──────────────────────────────────────┐
│                   RagController (REST API)                   │
│  - POST /api/rag/upload/pdf                                 │
│  - POST /api/rag/upload/file                                │
│  - POST /api/rag/chat                                       │
│  - POST /api/rag/memory/clear/user                          │
│  - POST /api/rag/memory/clear/all                           │
└──┬──────────────────────┬──────────────────────┬────────────┘
   │                      │                      │
   ▼                      ▼                      ▼
┌─────────────┐   ┌──────────────┐    ┌─────────────────┐
│FileUpload   │   │RagChat       │    │Memory Management│
│Service      │   │Service       │    │                 │
└──────┬──────┘   └──────┬───────┘    └────────┬────────┘
       │                  │                     │
       ▼                  ▼                     ▼
┌─────────────┐   ┌──────────────┐    ┌─────────────────┐
│DocumentLoad │   │LangChain4j   │    │RedisChatMemory  │
│Service      │   │AiServices    │    │Store            │
└──────┬──────┘   └──────┬───────┘    └────────┬────────┘
       │                  │                     │
       ▼                  ▼                     ▼
  ┌──────────┐    ┌──────────────┐    ┌─────────────────┐
  │Temp File │    │Retrieval     │    │   Redis         │
  │ Storage  │    │Augmentor     │    │   (TTL: 7 days) │
  └──────────┘    └──────┬───────┘    └─────────────────┘
                         │
                         ▼
              ┌──────────────────────┐
              │  Embedding Store     │
              │  Chroma / Milvus     │
              └──────────┬───────────┘
                         │
                         ▼
              ┌──────────────────────┐
              │  Ollama Services     │
              │  - LLM (qwen:7b)     │
              │  - Embedding         │
              │  - Reranker          │
              └──────────────────────┘
```

---

## 🔧 核心组件详解

### 1. LangChainConfig - LangChain4j 核心配置

**职责**: 装配所有 LangChain4j 相关的 Bean

**关键 Bean**:

#### ChatLanguageModel
```java
@Bean
public ChatLanguageModel chatLanguageModel(RagProperties properties) {
    return OllamaChatModel.builder()
            .baseUrl(properties.getOllama().getBaseUrl())
            .modelName(properties.getOllama().getLlmModel())
            .temperature(properties.getOllama().getTemperature())
            .build();
}
```

#### EmbeddingModel
```java
@Bean
public EmbeddingModel embeddingModel(RagProperties properties) {
    return OllamaEmbeddingModel.builder()
            .baseUrl(properties.getOllama().getBaseUrl())
            .modelName(properties.getOllama().getEmbeddingModel())
            .build();
}
```

#### RerankingModel
```java
@Bean
public RerankingModel rerankingModel(RagProperties properties) {
    return OllamaRerankingModel.builder()
            .baseUrl(properties.getOllama().getBaseUrl())
            .modelName(properties.getOllama().getRerankModel())
            .build();
}
```

#### EmbeddingStore
```java
@Bean
public EmbeddingStore<?> embeddingStore(RagProperties properties) {
    // Chroma 实现
    return ChromaEmbeddingStore.builder()
            .baseUrl(properties.getChroma().getBaseUrl())
            .collectionName(properties.getChroma().getCollectionName())
            .build();
    
    // Milvus 实现（生产环境）
    // return MilvusEmbeddingStore.builder()...
}
```

#### RetrievalAugmentor (带 Rerank)
```java
@Bean
public RetrievalAugmentor retrievalAugmentor(
        EmbeddingStore<?> embeddingStore,
        EmbeddingModel embeddingModel,
        RerankingModel rerankingModel,
        RagProperties properties) {
    
    ContentRetriever contentRetriever = 
        RerankingContentRetriever.builder()
            .embeddingStore(embeddingStore)
            .embeddingModel(embeddingModel)
            .rerankingModel(rerankingModel)
            .maxResults(properties.getRetrieve().getTopK())
            .minScore(0.6)
            .rerankMaxResults(properties.getRetrieve().getRerankTopK())
            .build();
    
    return DefaultRetrievalAugmentor.builder()
            .contentRetriever(contentRetriever)
            .build();
}
```

#### ChatMemoryProvider (多用户隔离)
```java
@Bean
public ChatMemoryProvider chatMemoryProvider(
        RedisChatMemoryStore chatMemoryStore,
        RagProperties properties) {
    
    return memoryId -> MessageWindowChatMemory.builder()
            .id(memoryId)
            .maxMessages(properties.getChatMemory().getMaxMessages())
            .chatMemoryStore(chatMemoryStore)
            .build();
}
```

---

### 2. RedisChatMemoryStore - Redis 持久化记忆存储

**职责**: 实现 ChatMemoryStore 接口，将对话历史持久化到 Redis

**核心方法**:

```java
@Override
public void addMessages(Object memoryId, List<ChatMessage> messages) {
    String key = buildKey(memoryId);
    redisTemplate.opsForList().rightPushAll(key, messages);
    redisTemplate.expire(key, ttl, TimeUnit.SECONDS);
}

@Override
public List<ChatMessage> getMessages(Object memoryId) {
    String key = buildKey(memoryId);
    return redisTemplate.opsForList().range(key, 0, -1);
}

@Override
public void deleteMessages(Object memoryId) {
    if ("*".equals(memoryId)) {
        // 清空全部记忆
        Set<String> keys = redisTemplate.keys(PREFIX + "*");
        redisTemplate.delete(keys);
    } else {
        // 清空单个用户记忆
        String key = buildKey(memoryId);
        redisTemplate.delete(key);
    }
}
```

**特性**:
- 按 userId 隔离会话
- TTL 过期策略（默认 7 天）
- 支持通配符批量删除
- JSON 序列化存储

---

### 3. DocumentLoadService - 文档加载服务

**职责**: 解析文档、文本切片、向量化入库

**核心流程**:

```java
public String loadAndVectorize(Path filePath) {
    // 1. 解析文档
    TextSegmentLoader loader = switch (fileExtension) {
        case "pdf" -> PdfDocumentLoader.create(filePath.toString());
        case "txt" -> TextDocumentLoader.create(filePath.toString());
        case "docx" -> DocxDocumentLoader.create(filePath.toString());
        case "md" -> MarkdownDocumentLoader.create(filePath.toString());
        default -> throw new IllegalArgumentException("Unsupported format");
    };
    
    List<TextSegment> segments = loader.load();
    
    // 2. 文本切片
    DocumentSplitter splitter = DocumentSplitters.recursive(
            chunkSize,    // 500
            chunkOverlap  // 80
    );
    List<TextSegment> chunks = splitter.split(segments);
    
    // 3. 过滤空内容
    chunks = chunks.stream()
            .filter(segment -> !segment.text().isBlank())
            .collect(Collectors.toList());
    
    // 4. 向量化并存入向量库
    embeddingStore.addAll(chunks);
    
    return String.format("文档导入成功，共分割：%d 个文本片段", chunks.size());
}
```

**支持的格式**:
- PDF (Apache PdfBox)
- TXT (原生支持)
- DOCX (Apache POI)
- MD (Markdown 解析器)

---

### 4. RagChatService - RAG 聊天服务

**职责**: 基于 AiServices 构建 AI 助手，集成检索增强和对话记忆

**核心实现**:

```java
interface Assistant {
    @SystemMessage("""
        你是一个智能客服助手。请基于以下知识库内容回答用户问题：
        
        {{contents}}
        
        要求：
        1. 仅基于上述知识库内容回答，不要编造信息
        2. 如果知识库中没有相关信息，明确告知用户
        3. 回答要简洁、准确、友好
        4. 使用中文回答
        """)
    String chat(@MemoryId String userId, @UserMessage String question);
}

@Service
public class RagChatService {
    
    private final Assistant assistant;
    
    public RagChatService(ChatLanguageModel chatModel,
                         RetrievalAugmentor augmentor,
                         ChatMemoryProvider memoryProvider) {
        this.assistant = AiServices.builder(Assistant.class)
                .chatLanguageModel(chatModel)
                .retrievalAugmentor(augmentor)
                .chatMemoryProvider(memoryProvider)
                .build();
    }
    
    public String chat(String userId, String question) {
        return assistant.chat(userId, question);
    }
    
    public void clearUserMemory(String userId) {
        // 清空指定用户记忆
    }
    
    public void clearAllMemory() {
        // 清空全部记忆
    }
}
```

**特性**:
- @MemoryId 实现用户级会话隔离
- @SystemMessage 定义自定义 Prompt 模板
- 自动绑定检索增强器
- 滑动窗口记忆管理

---

### 5. FileUploadService - 文件上传服务

**职责**: 处理文件上传、临时文件管理、自动清理

**核心实现**:

```java
@Service
public class FileUploadService {
    
    @Value("${upload.temp-path}")
    private String tempPath;
    
    public Path uploadFile(MultipartFile file) throws IOException {
        // 1. 创建临时目录
        Path tempDir = Paths.get(tempPath);
        Files.createDirectories(tempDir);
        
        // 2. UUID 生成唯一文件名
        String originalFilename = file.getOriginalFilename();
        String extension = getFileExtension(originalFilename);
        String uniqueFilename = UUID.randomUUID() + "." + extension;
        
        // 3. 保存文件
        Path filePath = tempDir.resolve(uniqueFilename);
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
        
        log.info("文件上传成功: {}", filePath);
        return filePath;
    }
    
    public void cleanupTempFile(Path filePath) {
        try {
            Files.deleteIfExists(filePath);
            log.info("临时文件已清理: {}", filePath);
        } catch (IOException e) {
            log.warn("临时文件清理失败: {}", filePath, e);
        }
    }
}
```

**特性**:
- UUID 防重名
- 自动清理机制
- 异常处理和日志记录

---

## 🔄 数据流详解

### 文档入库流程

```
1. 用户上传文件
   ↓
2. FileUploadService.uploadFile()
   - 保存到临时目录
   - UUID 命名
   ↓
3. DocumentLoadService.loadAndVectorize()
   a. 解析文档 (TextSegmentLoader)
   b. 文本切片 (DocumentSplitter)
   c. 过滤空内容
   d. 向量化 (EmbeddingModel)
   e. 存入向量库 (EmbeddingStore)
   ↓
4. FileUploadService.cleanupTempFile()
   - 删除临时文件
   ↓
5. 返回结果（切片数量）
```

### 智能问答流程

```
1. 用户提问 (userId + question)
   ↓
2. RagChatService.chat()
   ↓
3. ChatMemoryProvider 获取该用户的历史对话
   ↓
4. RetrievalAugmentor 执行检索
   a. 问题向量化 (EmbeddingModel)
   b. 向量相似度搜索 (EmbeddingStore)
   c. Rerank 重排 (RerankingModel)
   d. 返回 Top-K 相关片段
   ↓
5. 组装 Prompt
   - System Message (自定义模板)
   - Retrieved Contents (检索结果)
   - Chat History (对话历史)
   - User Question (当前问题)
   ↓
6. ChatLanguageModel 生成答案 (Ollama LLM)
   ↓
7. 保存对话历史到 Redis
   ↓
8. 返回答案给用户
```

---

## 🎯 关键技术点

### 1. 多用户会话隔离

**实现原理**:
- 使用 `@MemoryId` 注解标识用户 ID
- `ChatMemoryProvider` 为每个 userId 创建独立的 `MessageWindowChatMemory`
- Redis 中按 `rag:chat_memory:{userId}` 存储

**优势**:
- 完全隔离，互不干扰
- 支持并发访问
- Redis 持久化，重启不丢失

---

### 2. 两阶段检索优化

**第一阶段: 向量召回**
```
问题向量 → Chroma/Milvus 相似度搜索 → Top-50 候选
```

**第二阶段: Rerank 重排**
```
Top-50 候选 + 原始问题 → BGE-Reranker 评分 → Top-5 最终结果
```

**优势**:
- 提高检索精度
- 减少噪声干扰
- 提升 LLM 回答质量

---

### 3. 防幻觉机制

**自定义 Prompt 模板**:
```
你是一个智能客服助手。请基于以下知识库内容回答用户问题：

{{contents}}

要求：
1. 仅基于上述知识库内容回答，不要编造信息
2. 如果知识库中没有相关信息，明确告知用户
3. 回答要简洁、准确、友好
4. 使用中文回答
```

**效果**:
- 限制 AI 回答范围
- 避免编造不存在的信息
- 提高答案可信度

---

### 4. 滑动窗口记忆

**配置**:
```yaml
rag:
  chat-memory:
    max-messages: 20  # 最多保留 20 条消息（10 轮对话）
```

**原理**:
- 超出窗口大小的旧消息自动丢弃
- 保持上下文连贯性
- 控制 Token 消耗

---

## 📊 性能指标

### 典型性能数据

| 操作 | 耗时 | 说明 |
|------|------|------|
| 文档解析 (100页PDF) | 2-5秒 | 取决于文档复杂度 |
| 文本切片 | <1秒 | 递归切片算法 |
| 向量化 (单片段) | 100-300ms | Ollama Embedding |
| 向量检索 | 50-100ms | Chroma/Milvus |
| Rerank 重排 | 200-500ms | BGE-Reranker |
| LLM 生成答案 | 1-3秒 | qwen:7b，取决于答案长度 |
| **总耗时 (问答)** | **2-5秒** | 端到端 |

### 优化方向

1. **异步处理**: 文档入库改为异步
2. **缓存**: 热点问答结果缓存
3. **批量化**: 批量向量化提升吞吐
4. **模型优化**: 使用更小的模型或量化版本
5. **硬件加速**: GPU 推理加速

---

## 🔒 安全设计

### 1. 数据隐私
- 所有组件本地部署，数据不出内网
- Redis 持久化数据加密存储（可选）
- 临时文件自动清理

### 2. 访问控制
- 建议接入 JWT/OAuth2 认证
- 按 userId 隔离会话
- 可添加权限控制层

### 3. 输入验证
- 文件大小限制
- 文件格式白名单
- 敏感词过滤（可扩展）

---

## 🚀 扩展性设计

### 水平扩展

1. **向量库**: Milvus 支持分布式集群
2. **LLM 服务**: Ollama 可多实例负载均衡
3. **Redis**: Redis Cluster 支持
4. **应用服务**: Spring Boot 无状态设计，可多实例

### 功能扩展

1. **更多文档格式**: Excel、PPT、HTML 等
2. **多租户**: 按租户隔离知识库
3. **权限管理**: 细粒度访问控制
4. **审计日志**: 记录所有操作
5. **流式响应**: SSE 实时输出
6. **对话导出**: 导出聊天记录

---

## 📚 相关文档

- [RAG 使用指南](README.md) - 快速开始和 API 说明
- [模块详细说明](../architecture/modules.md) - ai-cs-knowledge 模块
- [配置管理](../deployment/configuration.md) - 配置项详解

---

**最后更新**: 2026-06-23
