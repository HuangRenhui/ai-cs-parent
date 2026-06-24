# ai-cs-agent vs ai-cs-knowledge 功能对比分析

## 📋 文档信息

- **文档版本**: v1.0
- **创建日期**: 2026-06-24
- **维护者**: huangrenhui
- **最后更新**: 2026-06-24
- **状态**: 两个模块互补并存

---

## 🎯 核心结论

**这两个模块功能不重复，而是互补关系！**

| 维度 | ai-cs-agent | ai-cs-knowledge |
|------|-------------|-----------------|
| **定位** | AI Agent智能体（任务执行） | RAG知识库系统（知识问答） |
| **核心能力** | 意图识别 + 工具调用 + 工单创建 | 文档检索 + 向量搜索 + 智能问答 |
| **技术栈** | 传统HTTP调用LLM + 规则引擎 | LangChain4j + 向量数据库 + Embedding + Rerank |
| **使用场景** | 任务型对话（查询、操作） | 知识型问答（咨询、学习） |
| **API端点** | `/ai/chat/send` | `/api/rag/chat`, `/api/faq/search` |
| **端口** | 9002 | 9003 |

---

## 📊 详细功能对比

### 1️⃣ ai-cs-agent（AI Agent智能体）

#### 核心定位
**任务型AI助手** - 负责理解用户意图，调用业务接口执行具体任务。

#### 主要功能

##### ✅ 意图识别
```java
// AiAgentService.java
public String processIntent(String userInput) {
    // 1. 调用LLM进行意图分类
    String intent = llmUtil.classifyIntent(userInput);
    
    // 2. 根据意图路由到不同处理器
    switch(intent) {
        case "QUERY_LOGISTICS":
            return queryLogistics(userInput);
        case "REFUND_REQUEST":
            return handleRefund(userInput);
        case "COMPLAINT":
            return handleComplaint(userInput);
        default:
            return generalChat(userInput);
    }
}
```

##### ✅ 工具调用（Function Calling）
```java
// AiToolEnum.java - 定义可用工具
public enum AiToolEnum {
    QUERY_LOGISTICS("查询物流", "/api/logistics/query"),
    CREATE_REFUND("申请退款", "/api/refund/create"),
    SUBMIT_COMPLAINT("提交投诉", "/api/complaint/submit"),
    CHECK_ORDER("查询订单", "/api/order/check");
}
```

##### ✅ 业务路由
- 通过Feign调用其他微服务
- 自动参数提取和验证
- 错误处理和重试机制

##### ✅ 工单创建
```java
// WorkOrderFeign.java
@FeignClient("ai-cs-workorder")
public interface WorkOrderFeign {
    @PostMapping("/workorder/create")
    Result<String> createWorkOrder(@RequestBody WorkOrderDTO dto);
}
```

#### 技术架构

```
用户输入 → LLM意图识别 → 规则引擎 → Feign调用业务服务 → 返回结果
         （classifyIntent）   （AiToolEnum）  （物流/退款/投诉）
```

**关键技术**：
- **LLM调用**：`LlmClient` - 直接调用OpenAI兼容API
- **意图分类**：基于Prompt工程的文本分类
- **服务发现**：Spring Cloud Feign
- **配置管理**：`@Value`注解

#### 典型使用场景

1. **查询物流**
   ```
   用户："我的订单到哪里了？"
   ↓
   Agent识别意图：QUERY_LOGISTICS
   ↓
   调用物流服务：logisticsService.query(orderId)
   ↓
   返回："您的订单已到达北京分拣中心"
   ```

2. **申请退款**
   ```
   用户："我要退款订单12345"
   ↓
   Agent识别意图：REFUND_REQUEST
   ↓
   提取参数：orderId=12345
   ↓
   调用退款服务：refundService.create(orderId)
   ↓
   创建工单并返回："退款申请已提交，工单号：WO20260624001"
   ```

3. **提交投诉**
   ```
   用户："我要投诉客服态度差"
   ↓
   Agent识别意图：COMPLAINT
   ↓
   调用投诉服务：complaintService.submit(content)
   ↓
   返回："投诉已记录，我们会尽快处理"
   ```

---

### 2️⃣ ai-cs-knowledge（RAG知识库系统）

#### 核心定位
**知识型问答系统** - 基于企业文档和FAQ，提供准确的智能问答服务。

#### 主要功能

##### ✅ 文档上传和向量化
```java
// DocumentService.java
@PostMapping("/document/upload")
public Result<String> uploadDocument(MultipartFile file) {
    // 1. 解析文档（PDF/Word/TXT）
    List<String> chunks = documentParser.parse(file);
    
    // 2. 文本分块
    List<String> segments = textSplitter.split(chunks);
    
    // 3. 向量化存储
    for (String segment : segments) {
        List<Float> vector = embeddingModel.embed(segment);
        chromaStore.add(vector, segment);
    }
    
    return Result.success("文档已向量化存储");
}
```

##### ✅ FAQ管理
```java
// FaqController.java
@GetMapping("/faq/search")
public Result<String> searchFaq(@RequestParam String question) {
    // 语义检索最相似的FAQ
    String answer = ragSearchService.semanticChat(question);
    return Result.success(answer);
}
```

##### ✅ 语义检索
支持两种检索模式：

**方案A：传统RAG（Milvus）**
```java
// RagSearchService.java
public String semanticChat(String question) {
    // 1. 问题向量化（外部Embedding服务）
    List<Float> vec = embeddingClient.getVector(question);
    
    // 2. Milvus语义检索
    List<String> relatedDocs = milvusUtil.search(vec, 3);
    
    // 3. 拼接约束Prompt
    String fullPrompt = buildRagPrompt(question, relatedDocs);
    
    // 4. 调用大模型（llm配置）
    return llmClient.call(fullPrompt);
}
```

**方案B：LangChain4j RAG（Chroma + Rerank）**
```java
// RagChatService.java
@Service
public class RagChatService {
    private final KnowledgeAssistant assistant;
    
    public RagChatService() {
        this.assistant = AiServices.builder(KnowledgeAssistant.class)
                .chatLanguageModel(chatModel)              // Ollama大模型
                .retrievalAugmentor(retrievalAugmentor)    // Chroma + Rerank
                .chatMemoryProvider(chatMemoryProvider)    // Redis对话记忆
                .build();
    }
    
    public String chat(String userId, String question) {
        return assistant.chat(userId, question);
    }
}
```

##### ✅ 多轮对话
- 基于Redis的对话记忆
- 上下文关联
- 会话超时管理

##### ✅ Rerank重排
```java
// LangChainConfig.java
@Bean
public ScoringModel scoringModel() {
    return new OllamaScoringModel(
            ragProperties.getOllama().getBaseUrl(),
            ragProperties.getOllama().getRerankModel()  // bge-reranker
    );
}

ContentAggregator aggregator = ReRankingContentAggregator.builder()
        .scoringModel(scoringModel)
        .minScore(0.6)
        .maxResults(3)
        .build();
```

#### 技术架构

**方案A：传统RAG**
```
用户问题 → Embedding向量化 → Milvus检索 → LLM生成答案
         （外部HTTP服务）    （Top-K=3）  （llm配置）
```

**方案B：LangChain4j RAG**
```
用户问题 → Chroma检索 → Rerank重排 → Ollama生成答案 → Redis存储对话
         （nomic-embed） （bge-reranker） （rag配置）  （chatMemory）
```

**关键技术**：
- **向量数据库**：Milvus / Chroma
- **Embedding模型**：外部API / nomic-embed-text
- **Rerank模型**：bge-reranker（可选）
- **框架**：LangChain4j 0.32.0
- **对话记忆**：Redis + Spring Data Redis

#### 典型使用场景

1. **产品知识问答**
   ```
   用户："这个产品的保修期是多久？"
   ↓
   系统从产品手册中检索相关段落
   ↓
   Rerank重排筛选最相关内容
   ↓
   回答："根据产品手册第3章，保修期为2年"
   ```

2. **FAQ快速检索**
   ```
   用户："怎么修改收货地址？"
   ↓
   系统在FAQ库中语义匹配
   ↓
   找到最相似的FAQ
   ↓
   回答："您可以在'个人中心 > 地址管理'中修改"
   ```

3. **多轮对话咨询**
   ```
   用户第一轮："我想了解退换货政策"
   ↓
   系统回答并存储对话历史
   ↓
   用户第二轮："那运费谁承担？"
   ↓
   系统结合上下文回答："如果是质量问题，我们承担运费"
   ```

---

## 🔍 核心差异对比

### 功能特性对比表

| 功能 | ai-cs-agent | ai-cs-knowledge |
|------|-------------|-----------------|
| **意图识别** | ✅ 核心功能 | ❌ 不支持 |
| **工具调用** | ✅ Feign远程调用 | ❌ 不支持 |
| **工单创建** | ✅ 自动创建 | ❌ 不支持 |
| **文档上传** | ❌ 不支持 | ✅ 核心功能 |
| **向量检索** | ❌ 不支持 | ✅ Milvus/Chroma |
| **Rerank重排** | ❌ 不支持 | ✅ BGE-Reranker |
| **多轮对话** | ⚠️ 简单支持 | ✅ Redis持久化 |
| **FAQ管理** | ❌ 不支持 | ✅ CRUD+检索 |
| **业务操作** | ✅ 查询/退款/投诉 | ❌ 只读问答 |
| **知识检索** | ❌ 不支持 | ✅ 语义搜索 |

### 技术实现对比表

| 技术维度 | ai-cs-agent | ai-cs-knowledge |
|----------|-------------|-----------------|
| **LLM调用方式** | 直接HTTP调用 | LangChain4j封装 |
| **配置方式** | `@Value`注解 | `@ConfigurationProperties` |
| **向量数据库** | 无 | Milvus / Chroma |
| **Embedding** | 无 | 外部API / Ollama |
| **Rerank** | 无 | Ollama BGE-Reranker |
| **对话记忆** | 内存临时存储 | Redis持久化 |
| **服务调用** | Spring Cloud Feign | 无 |
| **部署端口** | 9002 | 9003 |
| **依赖复杂度** | 低 | 高（需要向量库等） |

### 性能指标对比

| 指标 | ai-cs-agent | ai-cs-knowledge |
|------|-------------|-----------------|
| **响应时间** | 快（~500ms） | 中等（~1-2s，含检索） |
| **准确率** | 依赖意图识别 | 依赖文档质量+Rerank |
| **并发能力** | 高（无向量检索） | 中等（受向量库限制） |
| **资源占用** | 低 | 高（需运行向量库） |
| **扩展性** | 好（添加新工具） | 好（添加新文档） |

---

## 🤝 如何配合使用

### 典型业务流程

```
用户提问
   ↓
┌─────────────────────────┐
│  Gateway路由判断         │
└─────────────────────────┘
   ↓
   ├─ 任务型问题 → ai-cs-agent (9002)
   │                  ↓
   │             意图识别：查询物流/退款/投诉
   │                  ↓
   │             调用业务服务执行操作
   │                  ↓
   │             返回操作结果
   │
   └─ 知识型问题 → ai-cs-knowledge (9003)
                    ↓
               向量检索相关文档
                    ↓
               Rerank重排筛选
                    ↓
               生成知识性回答
```

### 示例场景

#### 场景1：用户先咨询后操作
```
用户："你们的退换货政策是什么？"
  → 路由到 ai-cs-knowledge
  → 检索产品手册，回答政策内容

用户："那我要退款订单12345"
  → 路由到 ai-cs-agent
  → 识别退款意图，调用退款服务
```

#### 场景2：复杂问题分解
```
用户："我的订单延误了，能帮我查一下物流并申请补偿吗？"
  → ai-cs-agent识别复合意图
  → 步骤1：调用物流服务查询
  → 步骤2：根据延误情况申请补偿工单
  → 综合返回结果
```

---

## 💡 设计建议

### 1. 明确分工

**ai-cs-agent负责**：
- ✅ 所有需要**执行业务操作**的场景
- ✅ 需要**调用外部服务**的任务
- ✅ **事务性**工作流（查询→判断→操作）

**ai-cs-knowledge负责**：
- ✅ 所有**知识性问答**场景
- ✅ 基于**企业文档**的检索
- ✅ **多轮对话**咨询

### 2. Gateway路由策略

建议在Gateway中添加智能路由：

```yaml
# application.yml
spring:
  cloud:
    gateway:
      routes:
        # 任务型路由
        - id: agent-task
          uri: lb://ai-cs-agent
          predicates:
            - Path=/ai/chat/**
            - Header=X-Chat-Type, task
          
        # 知识型路由
        - id: knowledge-qa
          uri: lb://ai-cs-knowledge
          predicates:
            - Path=/api/rag/**
            - Path=/api/faq/**
```

### 3. 前端调用建议

```javascript
// ChatPage.vue
async sendMessage(message) {
  // 判断消息类型
  const isTaskMessage = this.detectMessageType(message);
  
  if (isTaskMessage) {
    // 任务型 → 调用Agent
    const response = await axios.post('/ai/chat/send', { message });
  } else {
    // 知识型 → 调用Knowledge
    const response = await axios.post('/api/rag/chat', { 
      userId: this.userId,
      question: message 
    });
  }
}
```

---

## 🚀 未来优化方向

### 短期（1-2个月）

1. **保持两套系统独立**
   - 明确各自职责边界
   - 完善文档说明
   - 优化Gateway路由规则

2. **增强意图识别**
   - 在ai-cs-agent中添加更精准的意图分类
   - 支持更多业务场景

3. **提升检索质量**
   - 在ai-cs-knowledge中优化Rerank参数
   - 增加文档预处理流程

### 中期（3-6个月）

1. **统一对话管理**
   - 建立统一的对话历史服务
   - 跨模块共享上下文

2. **智能路由**
   - 在Gateway层实现意图预判
   - 自动路由到合适的服务

3. **融合方案探索**
   - 评估将ai-cs-agent集成到LangChain4j框架
   - 使用Function Calling替代规则引擎

### 长期（6-12个月）

1. **统一AI平台**
   - 合并为单一的AI服务
   - 模块化设计（Agent模块 + RAG模块）

2. **高级功能**
   - 多模态支持（图片、语音）
   - 个性化推荐
   - 情感分析

---

## 📝 总结

### 为什么会有两个模块？

这是**技术演进的自然结果**：

1. **ai-cs-agent**：早期开发，专注于任务执行，采用简单的HTTP调用LLM方式
2. **ai-cs-knowledge**：后期引入RAG能力，采用成熟的LangChain4j框架

### 是否需要合并？

**当前阶段不建议合并**，原因：

✅ **优势**：
- 职责清晰，易于维护
- 可独立扩展和部署
- 技术栈互不干扰

❌ **合并成本**：
- 需要重构大量代码
- 可能引入新的bug
- 收益不明显

**建议**：短期保持独立，中期评估后再决定是否融合。

### 最佳实践

1. **前端层面**：根据用户意图选择合适的服务调用
2. **后端层面**：通过Gateway统一管理路由
3. **运维层面**：分别监控两个服务的性能和可用性
4. **开发层面**：明确新功能应该添加到哪个模块

---

## 🔗 相关文档

- [RAG技术决策文档](./RAG_TECHNICAL_DECISION.md) - ai-cs-knowledge内部的两套RAG方案
- [模块重命名指南](./RENAME_AGENT_MODULE.md) - ai-cs-ai-agent → ai-cs-agent过程
- [系统架构概览](./overview.md) - 整体系统架构
- [快速开始指南](../QUICK_START.md) - 项目启动说明
