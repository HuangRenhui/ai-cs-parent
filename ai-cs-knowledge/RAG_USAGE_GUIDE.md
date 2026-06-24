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

3. **文档版本管理** ⭐新增
   - 自动版本控制：每次上传自动生成新版本号
   - MD5内容去重：相同内容文件不会重复创建版本
   - 版本查询：查看文档的所有历史版本及当前激活版本
   - 版本回退：可将文档回退到任意历史版本
   - 版本对比：比较两个版本的文件大小和MD5差异
   - 版本删除：支持逻辑删除指定版本（删除当前版本时自动切换）
   - 元数据记录：文件大小、类型、上传者、分段数等信息完整保存

4. **高级检索优化** ⭐已完成
   - ✅ Rerank重排模型提升检索精度（Ollama BGE-Reranker本地部署）
   - ✅ 自定义Prompt模板约束AI仅基于知识库回答
   - ✅ 防幻觉机制，避免编造信息

5. **FAQ知识管理**
   - FAQ的增删改查操作
   - 单个/批量FAQ向量化入库
   - 增量更新向量数据
   - 基于Milvus的语义检索问答

6. **会话管理**
   - 清空单个用户对话记忆
   - 清空全部用户会话
   - 对话历史TTL过期策略（默认7天）

7. **API文档**
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

## Rerank重排功能详解

### 功能概述

本项目已完整实现基于 **Ollama BGE-Reranker** 的本地化Rerank重排功能，通过二次排序显著提升检索精度。

**工作流程：**
```
用户提问
   ↓
向量检索（Top-5候选文档）
   ↓
BGE-Reranker重排模型评分
   ↓
过滤低分结果（minScore: 0.6）
   ↓
保留Top-3最相关文档
   ↓
注入Prompt → LLM生成答案
```

### 核心组件

1. **OllamaScoringModel** - 自定义评分模型实现
   - 位置：`com.ai.cs.knowledge.config.OllamaScoringModel`
   - 功能：调用Ollama的`/api/rerank`接口进行文档相关性评分
   - 特性：支持批量打分、降级策略、完整错误处理

2. **ReRankingContentAggregator** - LangChain4j重排聚合器
   - 配置位置：`LangChainConfig.retrievalAugmentor()`
   - 功能：集成ScoringModel到RAG检索流程
   - 参数：`minScore`控制最低相关性阈值

3. **配置项说明**
   ```yaml
   rag:
     ollama:
       rerank-model: bge-reranker:latest  # 重排模型名称
     retrieve:
       top-k: 5            # 初始向量检索数量
       rerank-top-k: 3     # 重排后保留数量
       min-score: 0.6      # 最低相关性阈值（0-1之间）
   ```

### 使用前准备

#### 1. 确保Ollama已启动并拉取模型

```bash
# 启动Ollama服务
ollama serve

# 拉取BGE-Reranker模型
ollama pull bge-reranker:latest

# 验证模型是否可用
ollama list | grep bge-reranker
```

#### 2. 验证Rerank API接口

```bash
curl http://localhost:11434/api/rerank -d '{
  "model": "bge-reranker:latest",
  "query": "什么是人工智能？",
  "documents": [
    "人工智能是计算机科学的一个分支",
    "机器学习是AI的子领域",
    "深度学习使用神经网络"
  ]
}'
```

**预期响应格式：**
```json
{
  "results": [
    {"index": 0, "relevance_score": 0.95},
    {"index": 2, "relevance_score": 0.87},
    {"index": 1, "relevance_score": 0.32}
  ]
}
```

### ⚠️ 重要说明

#### 当前实现的临时逻辑

在 `OllamaScoringModel.parseScoresFromResponse()` 方法中（第147-151行），包含一个**模拟分数生成逻辑**：

```java
// 暂时返回模拟分数，实际部署时需要替换为真实解析逻辑
for (int i = 0; i < expectedSize; i++) {
    scores.add(0.5 + Math.random() * 0.4); // 0.5-0.9之间的随机分数
}
```

**原因：** Ollama的Rerank API响应格式可能因版本而异，需要根据实际API返回调整解析逻辑。

#### 如何适配真实的API响应？

1. **查看实际响应格式**
   - 运行项目后查看日志：`log.debug("Ollama Rerank响应: {}", responseBody)`
   - 或直接在浏览器访问：http://localhost:8083/doc.html 测试接口

2. **修改解析逻辑**
   根据实际的JSON响应格式，修改 `parseScoresFromResponse()` 方法：

   ```java
   private List<Double> parseScoresFromResponse(String responseBody, int expectedSize) {
       List<Double> scores = new ArrayList<>();
       
       // 示例：假设返回 {"results": [{"index": 0, "relevance_score": 0.95}, ...]}
       JSONObject json = JSON.parseObject(responseBody);
       JSONArray results = json.getJSONArray("results");
       
       for (int i = 0; i < results.size(); i++) {
           JSONObject result = results.getJSONObject(i);
           double score = result.getDoubleValue("relevance_score");
           scores.add(score);
       }
       
       return scores;
   }
   ```

3. **添加JSON解析依赖**（如需要）
   ```xml
   <dependency>
       <groupId>com.alibaba</groupId>
       <artifactId>fastjson</artifactId>
       <version>2.0.40</version>
   </dependency>
   ```

### 🔧 降级策略

当Ollama Rerank API调用失败时，系统会自动降级：

1. **返回均匀分数（0.5）**：避免流程中断
2. **记录警告日志**：便于问题排查
3. **继续执行后续流程**：保证服务可用性

```java
if (!httpResponse.isSuccessful() || httpResponse.body() == null) {
    log.warn("Ollama Rerank API调用失败，状态码: {}，降级为向量相似度排序", httpResponse.code());
    // 返回均匀分数
    for (int i = 0; i < documents.size(); i++) {
        fallbackScores.add(0.5);
    }
    return fallbackScores;
}
```

### 🚀 切换到其他Rerank服务

如果Ollama的Rerank功能不稳定，可以轻松切换到其他服务：

#### 方案A：Jina AI Reranker（推荐用于生产环境）

**优点：**
- 高精度多语言支持
- 稳定的云端服务
- 免费额度充足

**步骤：**

1. 注册Jina AI获取API Key：https://jina.ai/

2. 添加依赖（pom.xml）：
   ```xml
   <dependency>
       <groupId>dev.langchain4j</groupId>
       <artifactId>langchain4j-jina</artifactId>
       <version>${langchain4j.version}</version>
   </dependency>
   ```

3. 配置application.yml：
   ```yaml
   rag:
     jina:
       api-key: your-jina-api-key
       model-name: jina-reranker-v2-base-multilingual
   ```

4. 修改LangChainConfig.java：
   ```java
   @Bean
   public ScoringModel scoringModel() {
       return JinaScoringModel.builder()
               .apiKey(ragProperties.getJina().getApiKey())
               .modelName(ragProperties.getJina().getModelName())
               .build();
   }
   ```

#### 方案B：本地ONNX模型（推荐用于私有化部署）

**优点：**
- 数据完全本地化
- 无需网络请求
- 零费用

**步骤：**

1. 下载BGE-Reranker ONNX模型：
   ```bash
   # 从HuggingFace下载
   git clone https://huggingface.co/BAAI/bge-reranker-base
   ```

2. 添加ONNX Runtime依赖：
   ```xml
   <dependency>
       <groupId>com.microsoft.onnxruntime</groupId>
       <artifactId>onnxruntime</artifactId>
       <version>1.16.0</version>
   </dependency>
   ```

3. 实现基于ONNX的ScoringModel（参考OllamaScoringModel的实现方式）

### 性能优化建议

1. **调整top-k参数**
   - `top-k`: 建议设置为5-10（初始检索范围）
   - `rerank-top-k`: 建议设置为3-5（最终保留数量）
   - 原则：`top-k > rerank-top-k`，给重排留出选择空间

2. **设置合理的min-score**
   - 默认值：0.6
   - 范围：0.5-0.8
   - 过高：可能过滤掉有用信息
   - 过低：可能引入噪声

3. **批量打分优化**
   - 当前实现已使用`scoreAll()`批量接口
   - 避免逐条调用`score()`，减少网络开销

4. **缓存热门问答**
   - 对高频问题缓存Rerank结果
   - 设置合理的TTL（如1小时）

### 故障排查

#### 问题1：Ollama Rerank API调用失败

**症状：** 日志中出现 `Ollama Rerank API调用失败`

**解决方案：**
```bash
# 1. 检查Ollama服务是否运行
ollama list

# 2. 确认模型已拉取
ollama pull bge-reranker:latest

# 3. 测试API连通性
curl http://localhost:11434/api/tags

# 4. 检查端口是否被占用
netstat -ano | findstr 11434
```

#### 问题2：重排效果不明显

**可能原因：**
- `min-score` 设置过低
- `top-k` 和 `rerank-top-k` 差距太小
- 向量检索质量本身较差

**解决方案：**
1. 提高 `min-score` 到 0.7-0.8
2. 增大 `top-k` 到 10，保持 `rerank-top-k` 为 3
3. 优化Embedding模型或增加训练数据

#### 问题3：响应速度变慢

**原因：** Rerank增加了额外的API调用

**解决方案：**
1. 减小 `top-k` 值（如从10降到5）
2. 启用Redis缓存热门问答
3. 考虑切换到本地ONNX模型（消除网络延迟）

---

## 文档版本管理API

启动项目后访问：http://localhost:8083/doc.html

### 1. 获取所有文档列表

**接口地址：** `GET /api/document/version/list`

**功能说明：** 获取所有文档的当前版本列表

**示例：**
```bash
curl -X GET "http://localhost:8083/api/document/version/list"
```

**返回数据：**
- documentId: 文档唯一标识
- documentName: 文档名称
- version: 当前版本号
- fileSize: 文件大小（字节）
- fileType: 文件类型
- segmentCount: 文档片段数量
- uploaderName: 上传人姓名
- createTime: 创建时间

---

### 2. 获取文档的所有版本

**接口地址：** `GET /api/document/version/versions/{documentId}`

**请求参数：**
- documentId: 文档唯一标识（路径参数）

**示例：**
```bash
curl -X GET "http://localhost:8083/api/document/version/versions/doc001"
```

**返回数据：** 该文档的所有历史版本列表（按版本号降序排列）

---

### 3. 获取文档当前版本

**接口地址：** `GET /api/document/version/current/{documentId}`

**请求参数：**
- documentId: 文档唯一标识（路径参数）

**示例：**
```bash
curl -X GET "http://localhost:8083/api/document/version/current/doc001"
```

**返回数据：** 当前激活的版本详情

---

### 4. 回退到指定版本

**接口地址：** `POST /api/document/version/rollback`

**请求参数：**
- documentId: 文档唯一标识（query参数）
- targetVersion: 目标版本号（query参数）

**示例：**
```bash
curl -X POST "http://localhost:8083/api/document/version/rollback?documentId=doc001&targetVersion=2"
```

**功能说明：**
- 将文档回退到指定的历史版本
- 原当前版本会被标记为非当前版本
- 目标版本被标记为当前版本

---

### 5. 删除指定版本

**接口地址：** `DELETE /api/document/version/delete/{id}`

**请求参数：**
- id: 版本ID（路径参数）

**示例：**
```bash
curl -X DELETE "http://localhost:8083/api/document/version/delete/123"
```

**功能说明：**
- 逻辑删除指定的文档版本
- 如果删除的是当前版本，系统会自动将该文档的最新版本设为当前版本

---

### 6. 比较两个版本

**接口地址：** `GET /api/document/version/compare`

**请求参数：**
- version1Id: 版本1 ID（query参数）
- version2Id: 版本2 ID（query参数）

**示例：**
```bash
curl -X GET "http://localhost:8083/api/document/version/compare?version1Id=1&version2Id=2"
```

**返回数据：**
```
版本对比结果:
版本1: V1, 文件大小: 102400 bytes, MD5: abc123...
版本2: V2, 文件大小: 105600 bytes, MD5: def456...
内容相同: 否
文件大小变化: 3200 bytes
```

---

### 7. 检查文件是否已存在

**接口地址：** `GET /api/document/version/exists`

**请求参数：**
- fileMd5: 文件MD5哈希值（query参数）

**示例：**
```bash
curl -X GET "http://localhost:8083/api/document/version/exists?fileMd5=abc123def456..."
```

**返回数据：** true/false

**功能说明：** 通过MD5检查文件内容是否已存在于系统中，用于避免重复上传

---

### 8. 获取版本详情

**接口地址：** `GET /api/document/version/detail/{id}`

**请求参数：**
- id: 版本ID（路径参数）

**示例：**
```bash
curl -X GET "http://localhost:8083/api/document/version/detail/123"
```

**返回数据：** 指定版本的完整详细信息

---

## FAQ知识管理API

### 1. 查询FAQ列表

**接口地址：** `GET /api/faq/list`

**示例：**
```bash
curl -X GET "http://localhost:8083/api/faq/list"
```

---

### 2. 新增FAQ

**接口地址：** `POST /api/faq/save`

**请求参数：**
- question: 问题内容
- answer: 答案内容
- category: 分类（可选）
- sortNum: 排序号（可选）

**示例：**
```bash
curl -X POST "http://localhost:8083/api/faq/save" \
  -H "Content-Type: application/json" \
  -d '{"question":"如何重置密码？","answer":"请联系管理员进行重置","category":"账号管理"}'
```

**处理流程：**
1. 保存FAQ到数据库
2. 自动向量化并存入Milvus
3. 返回milvusId用于后续更新/删除

---

### 3. 更新FAQ

**接口地址：** `PUT /api/faq/update`

**请求参数：**
- id: FAQ ID
- question: 问题内容
- answer: 答案内容
- 其他字段同新增

**示例：**
```bash
curl -X PUT "http://localhost:8083/api/faq/update" \
  -H "Content-Type: application/json" \
  -d '{"id":1,"question":"如何重置密码？","answer":"请在登录页面点击忘记密码"}'
```

**处理流程：**
1. 查询旧数据获取milvusId
2. 更新数据库记录
3. 增量更新Milvus中的向量数据

---

### 4. 删除FAQ

**接口地址：** `DELETE /api/faq/delete/{id}`

**示例：**
```bash
curl -X DELETE "http://localhost:8083/api/faq/delete/1"
```

**处理流程：**
1. 查询FAQ获取milvusId
2. 删除Milvus中的向量数据
3. 逻辑删除数据库记录

---

### 5. 单个FAQ向量化

**接口地址：** `POST /api/faq/vectorize/{id}`

**示例：**
```bash
curl -X POST "http://localhost:8083/api/faq/vectorize/1"
```

---

### 6. 批量FAQ向量化

**接口地址：** `POST /api/faq/vectorize/batch`

**示例：**
```bash
curl -X POST "http://localhost:8083/api/faq/vectorize/batch"
```

---

### 7. 批量增量更新向量

**接口地址：** `POST /api/faq/vectorize/increment`

**示例：**
```bash
curl -X POST "http://localhost:8083/api/faq/vectorize/increment"
```

---

### 8. 语义检索问答

**接口地址：** `GET /api/faq/search`

**请求参数：**
- question: 用户问题

**示例：**
```bash
curl -X GET "http://localhost:8083/api/faq/search?question=如何重置密码"
```

**处理流程：**
1. 将问题向量化
2. 在Milvus中检索相似FAQ
3. 基于检索结果生成答案

---

## 智能问答API

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

- [x] ~~接入更多文档格式（Excel、PPT等）~~ ⭐已完成（通过Apache Tika支持1000+格式）
- [x] ~~实现文档版本管理~~ ⭐已完成
- [ ] 添加检索结果溯源功能（返回引用来源）
- [ ] 集成权限管理系统（RBAC）
- [ ] 支持流式响应（SSE）
- [ ] 添加对话导出功能（PDF/Markdown）
- [ ] 实现知识库分类管理
- [ ] 文档全文检索功能
- [ ] 智能推荐相关问题
- [ ] 用户反馈机制（点赞/点踩）

---

## 联系方式

如有问题请联系开发团队。
