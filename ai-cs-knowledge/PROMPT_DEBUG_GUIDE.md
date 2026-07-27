# Prompt 调试指南（六大策略）

> AI 智能客服系统 Prompt 调试功能完整使用指南  
> 更新时间：2026-07-27

---

## 📋 目录

- [概述](#概述)
- [六大策略详解](#六大策略详解)
- [8种预设模板](#8种预设模板)
- [配置方式](#配置方式)
- [调试API](#调试api)
- [代码集成](#代码集成)
- [最佳实践](#最佳实践)

---

## 概述

Prompt 调试功能通过统一的 `PromptTemplateService` 服务，为所有 LLM 调用场景提供六大策略增强：

| 策略 | 说明 | 解决的问题 |
|------|------|-----------|
| 角色限定 | System Prompt 中指定身份和专业领域 | 回答风格不统一、缺乏专业视角 |
| 格式约束 | 指定 text/json/table/bullet/strict 输出 | 输出格式不可控、难以解析 |
| CoT思维链 | 逐步推理指令，分步思考 | 数学/逻辑问题准确率低 |
| Few-shot少样本 | 附带1-3个标准答案示例 | 输出格式和风格不稳定 |
| 边界约束 | 禁止编造、无数据兜底回复 | 模型幻觉、编造不存在的信息 |
| 上下文约束 | 窗口大小限制、自动截断 | 上下文过长超限、信息冗余 |

**架构设计**：

```
application.yml / 运行时API
         │
         ▼
PromptProperties（配置属性）
         │
         ▼
PromptTemplateService（模板构建）
    ├── buildSystemPrompt()  → 角色+格式+边界约束
    ├── buildUserPrompt()    → 上下文+CoT+Few-shot+问题
    └── buildMessages()      → OpenAI兼容 messages 数组
         │
         ▼
LlmClient.callWithSystem(systemPrompt, userPrompt)
         │
         ▼
LLM 大模型
```

---

## 六大策略详解

### 1. 角色限定

指定模型的角色身份和专业领域，放在 System Prompt 最前面。

**配置项**：

| 属性 | 类型 | 说明 | 示例 |
|------|------|------|------|
| systemRole | String | 系统角色 | "Java后端工程师" |
| roleDescription | String | 角色具体描述 | "你是一名经验丰富的Java后端工程师..." |
| roleDomain | String | 专业领域 | "Java后端开发、Spring生态、微服务" |

**生成的 System Prompt**：
```
你是Java后端工程师。你是一名经验丰富的Java后端工程师，精通Spring Boot、微服务架构、数据库设计和系统优化。你的专业领域包括：Java后端开发、Spring生态、微服务、分布式系统。
```

### 2. 格式约束

控制模型输出格式，支持5种模式：

| 格式 | 说明 | 适用场景 |
|------|------|----------|
| text | 普通文本（默认） | 一般对话 |
| json | 严格JSON格式，支持Schema字段约束 | API返回、数据解析 |
| table | Markdown表格格式 | 数据对比、清单展示 |
| bullet | 要点列表（- 开头） | 多要点回答 |
| strict | 只输出最终答案，禁止多余话术 | 简短直接回答 |

**JSON Schema 示例**：
```yaml
rag:
  prompt:
    output-format: json
    json-schema:
      name: "名称"
      description: "描述"
      status: "状态"
```

### 3. CoT 思维链

让模型分步思考，逐步推理后给出答案。放在 User Prompt 中作为推理指令。

**配置项**：

| 属性 | 类型 | 说明 |
|------|------|------|
| cotEnabled | Boolean | 是否启用 |
| cotInstruction | String | 推理指令模板 |
| cotSteps | List | 自定义推理步骤 |

**生成效果**：
```
【推理要求 - 请逐步思考】
请按以下步骤推理：
步骤1：理解问题
步骤2：列出已知条件
步骤3：逐步推导
步骤4：给出答案
```

### 4. Few-shot 少样本

附带1-3个标准答案示例，模型会复刻示例的格式和逻辑。

**配置示例**：
```yaml
rag:
  prompt:
    few-shot-enabled: true
    few-shot-examples:
      - question: "如何重置密码？"
        answer: "请前往设置-安全中心-修改密码..."
        reasoning: "先确认用户身份，再指导操作步骤"
```

### 5. 边界约束

全局约束规则，放在 System Prompt 中，防止模型编造数据。

**配置项**：

| 属性 | 类型 | 说明 | 默认值 |
|------|------|------|--------|
| boundaryEnabled | Boolean | 是否启用 | true |
| noFabrication | Boolean | 禁止编造数据 | true |
| noDataReply | String | 无数据兜底回复 | "暂无相关数据，无法回答此问题。" |
| additionalConstraints | String | 额外禁止行为 | "" |

### 6. 上下文约束

控制检索文档的上下文窗口，避免超出模型限制。

**配置项**：

| 属性 | 类型 | 说明 | 默认值 |
|------|------|------|--------|
| contextWindowSize | Integer | 上下文窗口大小（字符） | 4000 |
| contextOnlyReply | Boolean | 仅基于上下文回答 | true |
| contextPrefix | String | 上下文前缀标识 | "【参考文档】" |
| userPrefix | String | 用户问题前缀 | "【用户问题】" |

---

## 8种预设模板

| 预设名 | 系统角色 | 输出格式 | CoT | Few-shot | 适用场景 |
|--------|----------|----------|-----|----------|----------|
| java-engineer | Java后端工程师 | bullet | - | - | 技术问答，要点清晰 |
| interviewer | 业务面试官 | text | - | - | 面试评估，专业对话 |
| json-output | 数据分析助手 | json | - | - | 结构化输出，便于解析 |
| cot-reasoning | 逻辑推理专家 | text | ✓ | - | 数学/逻辑问题，逐步推理 |
| few-shot | 智能客服助手 | text | - | ✓ | 客服场景，示例驱动 |
| strict-boundary | 知识库问答助手 | text | - | - | 严格防幻觉，仅基于文档 |
| table-output | 数据分析师 | table | - | - | 数据对比，表格展示 |
| full-combo | 资深技术顾问 | bullet | ✓ | ✓ | 全功能组合，专业回答 |

**一键切换**：
```yaml
rag:
  prompt:
    preset: java-engineer  # 切换预设
```

---

## 配置方式

### 方式一：application.yml（持久化）

```yaml
rag:
  prompt:
    enabled: true
    debug-log: false
    preset: ""                         # 为空则使用下方自定义配置
    # === 1. 角色限定 ===
    system-role: "Java后端工程师"
    role-description: "你是一名经验丰富的Java后端工程师..."
    role-domain: "Java后端开发、Spring生态、微服务、分布式系统"
    # === 2. 格式约束 ===
    output-format: bullet
    strict-output: false
    # === 3. CoT 思维链 ===
    cot-enabled: false
    cot-instruction: "请一步步思考..."
    # === 4. Few-shot 少样本 ===
    few-shot-enabled: false
    # === 5. 边界约束 ===
    boundary-enabled: true
    no-data-reply: "暂无相关数据，无法回答此问题。"
    no-fabrication: true
    # === 6. 上下文约束 ===
    context-window-size: 4000
    context-only-reply: true
    context-prefix: "【参考文档】"
    system-prefix: "【系统指令】"
    user-prefix: "【用户问题】"
```

### 方式二：运行时API（临时）

通过 `POST /api/prompt/config` 动态修改，重启后恢复。

### 方式三：环境变量

```bash
export PROMPT_PRESET=java-engineer
export PROMPT_COT_ENABLED=true
export PROMPT_DEBUG_LOG=true
```

---

## 调试API

### 查看当前配置
```http
GET /api/prompt/config
```

### 查看所有预设
```http
GET /api/prompt/presets
```

### 查看预设详情
```http
GET /api/prompt/presets/java-engineer
```

### 预览 System Prompt
```http
GET /api/prompt/preview/system
```

### 预览完整 Prompt（带模拟上下文）
```http
POST /api/prompt/preview/full
Content-Type: application/json

{
  "question": "如何优化数据库查询性能？",
  "contextDocs": ["索引优化是关键...", "慢查询日志帮助定位..."]
}
```

### 预览 OpenAI Messages 格式
```http
POST /api/prompt/preview/messages
Content-Type: application/json

{
  "question": "什么是微服务？",
  "contextDocs": ["微服务是一种架构风格..."]
}
```

### 动态修改配置
```http
POST /api/prompt/config
Content-Type: application/json

{
  "preset": "full-combo",
  "debugLog": true
}
```

### 重置配置
```http
POST /api/prompt/reset
```

---

## 代码集成

### 在 Service 中使用（推荐）

```java
@Service
public class YourService {

    @Resource
    private PromptTemplateService promptTemplateService;
    @Resource
    private LlmClient llmClient;

    public String answer(String question, List<String> docs) throws IOException {
        // 构建 System + User 双消息
        String systemPrompt = promptTemplateService.buildSystemPrompt();
        String userPrompt = promptTemplateService.buildUserPrompt(question, docs);

        // 调用 LLM
        return llmClient.callWithSystem(systemPrompt, userPrompt);
    }
}
```

### 获取完整 messages 数组（用于 LangChain4j）

```java
List<Map<String, Object>> messages = promptTemplateService.buildMessages(question, docs);
// messages = [
//   {"role": "system", "content": "..."},
//   {"role": "user", "content": "..."}
// ]
```

### 获取合并后的单字符串 Prompt（兼容旧版）

```java
String fullPrompt = promptTemplateService.buildFullPrompt(question, docs);
String answer = llmClient.call(fullPrompt);
```

### 已集成的服务

以下服务已自动集成 PromptTemplateService，无需额外修改：

| 服务 | 集成方式 |
|------|----------|
| RagSearchService | `buildSystemPrompt()` + `buildUserPrompt()` |
| HybridRetrievalService | 多模态检索 + System Prompt |
| GraphRagService | 知识图谱问答 + System Prompt |
| MixedModalityChatService | 图文混合对话 + User Prompt |

---

## 最佳实践

### 1. 不同场景选择不同预设

| 场景 | 推荐预设 |
|------|----------|
| 内部知识库问答 | strict-boundary（严格防幻觉） |
| 技术文档问答 | java-engineer（专业+要点） |
| 面试/评估 | interviewer（专业对话） |
| 数据接口返回 | json-output（结构化） |
| 客服场景 | few-shot（示例驱动） |
| 数据分析 | table-output（表格展示） |
| 数学/逻辑问题 | cot-reasoning（逐步推理） |
| 综合场景 | full-combo（全功能） |

### 2. 调试流程

1. 开启 debug-log：`POST /api/prompt/config {"debugLog": true}`
2. 预览 System Prompt：`GET /api/prompt/preview/system`
3. 预览完整 Prompt：`POST /api/prompt/preview/full`
4. 检查日志中的实际 Prompt 内容
5. 根据效果调整配置
6. 确认后写入 application.yml 固化

### 3. 上下文窗口管理

- 默认 4000 字符窗口，适配大多数模型
- 如果模型支持更大窗口，可调大 `contextWindowSize`
- 配合 RetrievalOptimizerService 的上下文压缩，双重保障

### 4. 防幻觉策略

```
System Prompt（边界约束）
    ↓
禁止编造 + 无数据兜底 + 仅基于上下文
    ↓
User Prompt（上下文约束）
    ↓
检索文档 + 仅基于上文回答
    ↓
LLM 输出
```

---

## 相关文档

- [系统架构概览](../docs/architecture/overview.md)
- [模块详细说明](../docs/architecture/modules.md)
- [功能开发清单](../docs/功能开发清单.md)
- [API 接口文档](../docs/api/README.md)
- [RAG 使用指南](RAG_USAGE_GUIDE.md)
