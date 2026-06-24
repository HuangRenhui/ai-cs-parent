# ScoringModel扩展实践指南

## 概述

本文档详细介绍如何在LangChain4j框架中扩展和自定义ScoringModel（评分模型），用于RAG系统的检索结果重排序（Rerank）。

## 核心概念

### 什么是ScoringModel？

ScoringModel是LangChain4j提供的评分模型接口，用于对检索到的文档片段进行相关性打分。它是Rerank功能的核心组件。

```java
public interface ScoringModel {
    // 对单个文本段打分
    Response<Double> score(TextSegment segment, String query);
    
    // 批量打分（推荐使用）
    Response<List<Double>> scoreAll(List<TextSegment> segments, String query);
}
```

### 为什么需要Rerank？

**传统向量检索的局限性：**
- 仅基于语义相似度，可能忽略精确匹配
- 对于专业术语、缩写等识别能力有限
- 召回的相关文档可能不够精准

**Rerank的优势：**
- 使用Cross-Encoder架构，同时编码Query和Document
- 能够捕捉更细粒度的语义关系
- 显著提升Top-K结果的准确性（通常提升20-40%）

---

## 项目中的实现

### 1. OllamaScoringModel实现

**文件位置：** `com.ai.cs.knowledge.config.OllamaScoringModel`

**核心特性：**
- ✅ 基于Ollama本地部署的BGE-Reranker模型
- ✅ 支持批量打分（scoreAll方法）
- ✅ 降级策略：API失败时返回默认分数
- ✅ 完整的日志记录和错误处理
- ✅ JSON请求体构建和响应解析

**关键代码片段：**

```java
@Slf4j
public class OllamaScoringModel implements ScoringModel {

    private final String baseUrl;
    private final String modelName;
    private final OkHttpClient httpClient;

    public OllamaScoringModel(String baseUrl, String modelName) {
        this.baseUrl = baseUrl;
        this.modelName = modelName;
        this.httpClient = new OkHttpClient();
    }

    @Override
    public Response<List<Double>> scoreAll(List<TextSegment> segments, String query) {
        try {
            // 1. 提取文本内容
            List<String> documents = segments.stream()
                .map(TextSegment::text)
                .collect(Collectors.toList());

            // 2. 调用Ollama Rerank API
            List<Double> scores = callOllamaRerank(query, documents);
            return Response.from(scores);
        } catch (Exception e) {
            log.error("Ollama Rerank批量评分失败", e);
            // 降级策略：返回默认分数
            return Response.from(Collections.nCopies(segments.size(), 0.5));
        }
    }
}
```

### 2. Spring Bean配置

**文件位置：** `com.ai.cs.knowledge.config.LangChainConfig`

```java
@Bean
public ScoringModel scoringModel() {
    return new OllamaScoringModel(
        ragProperties.getOllama().getBaseUrl(),
        ragProperties.getOllama().getRerankModel()
    );
}

@Bean
public RetrievalAugmentor retrievalAugmentor(
        EmbeddingStore<TextSegment> embeddingStore,
        EmbeddingModel embeddingModel,
        ScoringModel scoringModel
) {
    // 向量检索器
    var contentRetriever = EmbeddingStoreContentRetriever.builder()
        .embeddingStore(embeddingStore)
        .embeddingModel(embeddingModel)
        .maxResults(ragProperties.getRetrieve().getTopK())
        .build();

    // Rerank聚合器
    ContentAggregator aggregator = ReRankingContentAggregator.builder()
        .scoringModel(scoringModel)
        .minScore(ragProperties.getRetrieve().getMinScore())
        .build();

    // 构建检索增强器
    return DefaultRetrievalAugmentor.builder()
        .contentRetriever(contentRetriever)
        .contentAggregator(aggregator)
        .build();
}
```

---

## 扩展其他ScoringModel实现

### 方案A：Jina AI Reranker（云端API）

**适用场景：** 生产环境、需要高精度、可接受网络延迟

**步骤：**

#### 1. 添加依赖

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-jina</artifactId>
    <version>${langchain4j.version}</version>
</dependency>
```

#### 2. 配置API Key

```yaml
rag:
  jina:
    api-key: jina_xxxxxxxxxxxxx  # 从 https://jina.ai/ 获取
    model-name: jina-reranker-v2-base-multilingual
```

#### 3. 创建Bean

```java
@Bean
public ScoringModel scoringModel() {
    return JinaScoringModel.builder()
        .apiKey(ragProperties.getJina().getApiKey())
        .modelName(ragProperties.getJina().getModelName())
        .build();
}
```

**优点：**
- 开箱即用，无需自己实现
- 支持多语言（中文、英文等）
- 定期更新模型

**缺点：**
- 需要网络连接
- 有免费额度限制（通常每月100万次调用）
- 数据会发送到云端

---

### 方案B：Cohere Rerank API

**适用场景：** 企业级应用、需要商业支持

**步骤：**

#### 1. 添加依赖

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-cohere</artifactId>
    <version>${langchain4j.version}</version>
</dependency>
```

#### 2. 配置

```yaml
rag:
  cohere:
    api-key: cohere_xxxxxxxxxxxxx
    model-name: rerank-multilingual-v3.0
```

#### 3. 创建Bean

```java
@Bean
public ScoringModel scoringModel() {
    return CohereScoringModel.builder()
        .apiKey(ragProperties.getCohere().getApiKey())
        .modelName(ragProperties.getCohere().getModelName())
        .build();
}
```

---

### 方案C：本地ONNX模型（推荐私有化部署）

**适用场景：** 数据敏感、无网络环境、零费用

**步骤：**

#### 1. 下载ONNX模型

```bash
# 从HuggingFace下载BGE-Reranker模型
git clone https://huggingface.co/BAAI/bge-reranker-base
cd bge-reranker-base

# 转换为ONNX格式（如果尚未提供）
pip install optimum[onnx]
optimum-export onnx --model BAAI/bge-reranker-base ./onnx_model
```

#### 2. 添加依赖

```xml
<dependency>
    <groupId>com.microsoft.onnxruntime</groupId>
    <artifactId>onnxruntime</artifactId>
    <version>1.16.0</version>
</dependency>

<dependency>
    <groupId>ai.djl.huggingface</groupId>
    <artifactId>tokenizers</artifactId>
    <version>0.28.0</version>
</dependency>
```

#### 3. 实现OnnxScoringModel

```java
@Slf4j
public class OnnxScoringModel implements ScoringModel {
    
    private final OrtSession session;
    private final HuggingFaceTokenizer tokenizer;
    private final int maxSequenceLength;

    public OnnxScoringModel(String modelPath, String tokenizerPath) throws Exception {
        // 加载ONNX模型
        OrtEnvironment env = OrtEnvironment.getEnvironment();
        this.session = env.createSession(modelPath);
        
        // 加载分词器
        this.tokenizer = HuggingFaceTokenizer.newInstance(new File(tokenizerPath));
        this.maxSequenceLength = 512;
    }

    @Override
    public Response<List<Double>> scoreAll(List<TextSegment> segments, String query) {
        List<Double> scores = new ArrayList<>();
        
        for (TextSegment segment : segments) {
            double score = computeScore(query, segment.text());
            scores.add(score);
        }
        
        return Response.from(scores);
    }

    private double computeScore(String query, String document) {
        try {
            // 1. 编码输入
            Encoding encoding = tokenizer.encode(query, document, true);
            long[] inputIds = encoding.getIds();
            long[] attentionMask = encoding.getAttentionMask();
            long[] tokenTypeIds = encoding.getTypeIds();

            // 2. 准备ONNX输入
            Map<String, OnnxTensor> inputs = new HashMap<>();
            inputs.put("input_ids", createTensor(inputIds));
            inputs.put("attention_mask", createTensor(attentionMask));
            inputs.put("token_type_ids", createTensor(tokenTypeIds));

            // 3. 运行推理
            OrtResult result = session.run(inputs);
            
            // 4. 提取分数
            float[][] logits = (float[][]) result.get(0).getValue();
            double score = sigmoid(logits[0][0]); // 应用Sigmoid激活函数
            
            return score;
        } catch (Exception e) {
            log.error("ONNX推理失败", e);
            return 0.5; // 降级分数
        }
    }

    private OnnxTensor createTensor(long[] data) throws OrtException {
        return OnnxTensor.createTensor(OrtEnvironment.getEnvironment(), 
            new long[][]{data});
    }

    private double sigmoid(double x) {
        return 1.0 / (1.0 + Math.exp(-x));
    }
}
```

#### 4. 配置Bean

```java
@Bean
public ScoringModel scoringModel() throws Exception {
    return new OnnxScoringModel(
        "./models/bge-reranker/model.onnx",
        "./models/bge-reranker/tokenizer.json"
    );
}
```

**优点：**
- 数据完全本地化
- 无网络延迟
- 零费用
- 可离线运行

**缺点：**
- 需要GPU加速才能获得好性能（CPU也可运行但较慢）
- 需要自己维护模型
- 初始配置较复杂

---

### 方案D：自定义HTTP API

**适用场景：** 已有内部Rerank服务、特殊定制需求

**参考实现：** 项目中的`OllamaScoringModel`就是这种方式的典型示例。

**关键要点：**
1. 实现`ScoringModel`接口
2. 使用OkHttpClient或RestTemplate调用HTTP API
3. 构建JSON请求体
4. 解析JSON响应
5. 实现降级策略

---

## 最佳实践

### 1. 参数调优

```yaml
rag:
  retrieve:
    top-k: 10              # 初始检索数量（建议5-15）
    rerank-top-k: 5        # 重排后保留数量（建议3-7）
    min-score: 0.6         # 最低阈值（建议0.5-0.8）
```

**调优原则：**
- `top-k` 应明显大于 `rerank-top-k`（至少2倍）
- `min-score` 过高会过滤掉有用信息，过低会引入噪声
- 根据实际业务场景AB测试调整

### 2. 性能优化

#### 批量打分
```java
// ✅ 推荐：批量调用
Response<List<Double>> response = scoringModel.scoreAll(segments, query);

// ❌ 避免：逐条调用
for (TextSegment segment : segments) {
    Response<Double> score = scoringModel.score(segment, query);
}
```

#### 缓存热门问答
```java
@Cacheable(value = "rerank", key = "#query + ':' + #documentHash")
public List<Double> getRerankScores(String query, List<String> documents) {
    return scoringModel.scoreAll(...).content();
}
```

#### 异步处理
```java
@Async
public CompletableFuture<List<Double>> asyncRerank(String query, List<TextSegment> segments) {
    return CompletableFuture.completedFuture(
        scoringModel.scoreAll(segments, query).content()
    );
}
```

### 3. 错误处理

```java
try {
    Response<List<Double>> response = scoringModel.scoreAll(segments, query);
    return response.content();
} catch (Exception e) {
    log.error("Rerank失败，降级为向量排序", e);
    // 降级策略1：返回均匀分数
    return Collections.nCopies(segments.size(), 0.5);
    
    // 降级策略2：直接返回原始向量检索结果
    // return originalVectorResults;
}
```

### 4. 监控与日志

```java
@Slf4j
public class MonitoredScoringModel implements ScoringModel {
    
    private final ScoringModel delegate;
    private final MeterRegistry meterRegistry;

    @Override
    public Response<List<Double>> scoreAll(List<TextSegment> segments, String query) {
        long start = System.currentTimeMillis();
        
        try {
            Response<List<Double>> result = delegate.scoreAll(segments, query);
            
            // 记录成功指标
            long duration = System.currentTimeMillis() - start;
            meterRegistry.timer("rerank.duration").record(duration, TimeUnit.MILLISECONDS);
            meterRegistry.counter("rerank.success").increment();
            
            log.debug("Rerank完成，耗时: {}ms, 文档数: {}", duration, segments.size());
            return result;
        } catch (Exception e) {
            // 记录失败指标
            meterRegistry.counter("rerank.failure").increment();
            log.error("Rerank失败", e);
            throw e;
        }
    }
}
```

---

## 常见问题

### Q1: 如何选择合适的Rerank模型？

**回答：**
- **中文场景**：BGE-Reranker、Jina Reranker v2
- **多语言场景**：Jina Reranker v2 Multilingual、Cohere rerank-multilingual
- **英文场景**：Cohere rerank-english、Jina Reranker
- **私有化部署**：BGE-Reranker ONNX、BGE-M3

### Q2: Rerank会增加多少延迟？

**回答：**
- **云端API**：通常增加100-500ms（取决于网络）
- **本地ONNX（GPU）**：增加10-50ms
- **本地ONNX（CPU）**：增加100-300ms

**优化建议：**
- 减小`top-k`值
- 启用缓存
- 使用GPU加速

### Q3: 是否需要同时使用向量检索和Rerank？

**回答：** 是的，这是标准做法。

**原因：**
- 向量检索：快速召回候选集（双编码器，效率高）
- Rerank：精排候选集（交叉编码器，精度高）
- 组合使用：平衡效率和精度

### Q4: 如何评估Rerank效果？

**回答：**

**定量指标：**
1. **MRR（Mean Reciprocal Rank）**：平均倒数排名
2. **NDCG@K**：归一化折损累计增益
3. **Precision@K**：前K个结果的准确率

**定性评估：**
1. 人工抽检重排前后的结果
2. A/B测试用户满意度
3. 统计用户追问率（越低说明答案越准确）

---

## 参考资料

- [LangChain4j官方文档 - RAG](https://docs.langchain4j.dev/tutorials/rag)
- [BGE-Reranker模型](https://huggingface.co/BAAI/bge-reranker-base)
- [Jina AI Reranker](https://jina.ai/reranker)
- [Cohere Rerank API](https://docs.cohere.com/docs/rerank)
- [ONNX Runtime](https://onnxruntime.ai/)

---

## 更新日志

- **2026-06-24**：初始版本，包含OllamaScoringModel实现和多种扩展方案
