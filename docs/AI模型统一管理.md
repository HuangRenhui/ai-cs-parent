# AI 模型统一注册与管理

> 目标：支持本地(Ollama)与在线(DashScope/OpenAI兼容/DeepSeek)模型自由切换、管理页注册/启停/设为生效、对话选用模型、以及模型故障时的自动切换（故障转移）。

## 1. 需求

1. **本地 / 在线自由切换**：同一套 LLM / Embedding / Rerank 能力可在本地 Ollama 与在线云端之间切换。
2. **管理页模型切换**：平台管理页提供下拉，选择"当前生效"模型，实时生效、无需重启。
3. **注册模型页面**：录入模型（供应方、能力、地址、密钥、上游模型名、优先级、启用状态）。
4. **对话选择模型服务**：对话可指定某模型，或使用默认（当前生效）模型。
5. **故障自动切换**：模型连接异常时自动切换到其他可用模型，并做健康检测恢复。

## 2. 架构

```
                 管理端(base-service /system/ai-model)
                        │ CRUD + 设生效 + 测试连接 + 健康
                        ▼
              ┌───────────────────────┐
              │ cs_ai_model (MySQL)   │ ──▶ Redis(生效配置广播)
              └───────────────────────┘
                        │ 读当前生效/候选
                        ▼
          ModelRouter (ai-cs-common, 各服务内嵌)
            ├─ Provider: OLLAMA(本地)
            ├─ Provider: DASHSCOPE / OPENAI / DEEPSEEK(在线)
            ├─ 调用失败计数 + 自动 fallback 到次优先模型
            └─ 周期性健康探测与恢复
                        │
      ┌──────────────┬──┴───────────┬──────────────┐
      ▼              ▼              ▼              ▼
  agent 对话   knowledge FAQ向量化  RAG对话/重排   多模态(Vision)
```

- **注册与状态**：`cs_ai_model` 表存全部模型，`enabled` 启停，`is_active` 标记同能力当前生效（程序保证同一 `model_type` 仅一条 active），`health` 记录健康状态，`priority` 决定故障切换顺序。
- **广播**：base-service 在增删改/设生效后，把生效模型与候选列表写入 Redis（全局共享），各消费服务据此路由，实现跨服务切换。
- **Provider 抽象**：所有 provider 用 **OpenAI 兼容 HTTP 协议**（`/v1/chat/completions`、`/v1/embeddings`）。Ollama 本地开启 `/v1` 兼容端点后与在线完全同路径，切换代码零差异。

## 3. 数据表

见 [ai_model.sql](database/ai_model.sql)（增量脚本），表名 `cs_ai_model`。

## 4. 后端改动

| 模块 | 内容 |
|------|------|
| ai-cs-common | `ModelProvider` 枚举(OLLAMA/DASHSCOPE/OPENAI/DEEPSEEK)；`OpenAiCompatClient`(chat/embed/rerank)；`ModelRouter`(读 Redis 生效配置、多 provider 分发、失败 fallback、健康探测) |
| ai-cs-base-service | `AiModelConfig` 实体 + Mapper + Service + `AiModelConfigController`(`/system/ai-model`)：CRUD、设生效、测试连接、健康状态、同步 Redis |
| ai-cs-agent | 对话改走 `ModelRouter`（替换直连 DashscopeModelClient） |
| ai-cs-knowledge | RAG 对话/重排、FAQ 向量化改走 `ModelRouter` |

## 5. 前端改动

| 页面 | 内容 |
|------|------|
| `/model` 模型管理页 | 列表注册/编辑/删除；测试连接；设为生效；启停；健康标识 |
| 顶部"当前模型"下拉 | 直接切换生效模型（实时） |
| 对话页模型选择 | 本次对话指定模型 |

## 6. 故障转移策略

- 调用失败：同模型先按 `max_retries` 重试（默认 1 次，超时重试对付费模型可能重复计费，慎用）
- 仍失败：连续失败达 `fail_threshold`(默认 2) → 该模型熔断摘除，冷却 60s 后半开探测恢复
- 从同能力、`enabled`、其余候选中按 `priority` 升序取次优模型继续本次调用
- 配额：达当日 `daily_token_limit` / `daily_cost_limit` 上限的模型自动顺延（把本地/免费模型注册为候选即获得"超限降级"）；达 80% 输出预警日志
- 每次调用记录 token、耗时、成本快照（按发生时单价）、成功/失败到 `cs_model_usage`（由 ai-cs-job 从 Redis 流消费落库）
- 全部 DOWN 时返回明确错误，不静默失败

## 7. 分阶段实施

- [x] 阶段一：建表 + base-service 模型管理后端（CRUD/生效/测试连接/健康 + Redis 广播）
- [x] 阶段二：common `ModelRouter` 与 OpenAI 兼容 Provider，本地+在线切换基座
- [x] 阶段三：agent / knowledge 消费端接入 Router（故障转移生效）
- [x] 阶段四：前端模型管理页（含当前对话模型切换下拉）
- [x] 阶段五：密钥加密（AES-GCM 落库加密、读取解密/脱敏回显）；init.sql 合并 cs_ai_model 已完成

## 8. 已落地实现（阶段一 + 阶段二）

### 8.1 ai-cs-common 新增（llm 包）

| 文件 | 说明 |
|------|------|
| `llm/ModelProviderEnum` | 供应方枚举 OLLAMA/DASHSCOPE/OPENAI/DEEPSEEK/OTHER |
| `llm/ModelTypeEnum` | 能力枚举 LLM/EMBEDDING/RERANK/VISION/MULTIMODAL |
| `llm/ModelHealthEnum` | 健康枚举 UNKNOWN/HEALTHY/DOWN |
| `llm/AiModelRoute` | 路由模型描述(与 cs_ai_model 字段对应，供 Redis/内存交换) |
| `llm/OpenAiCompatClient` | OpenAI 兼容 HTTP 客户端(chat/completions、embeddings)，无 Spring 依赖 |
| `llm/ModelRouter` | 模型路由器：按能力选生效模型、多候选 fallback、连通测试、Redis 刷新 |
| `constant/RedisKeyConst` | 新增 `AI_MODEL_REGISTRY` / `AI_MODEL_ACTIVE` / `AI_MODEL_VERSION` |

### 8.2 ai-cs-base-service 新增（/system/ai-model）

| 文件 | 说明 |
|------|------|
| `entity/AiModelConfig` | 实体(@TableName("cs_ai_model")) |
| `mapper/AiModelConfigMapper` | MyBatis-Plus Mapper |
| `service/AiModelConfigService` | CRUD/唯一生效/启停让渡/测试连接/Redis 广播/启动预热 |
| `controller/AiModelConfigController` | REST 接口(见下) |
| `resources/application.yml` | 新增 `spring.data.redis` 连接配置 |

### 8.3 REST 接口（/system/ai-model）

| 方法/路径 | 说明 |
|-----------|------|
| `GET /system/ai-model/list` | 全部模型 |
| `GET /system/ai-model/enabled?modelType=` | 某能力启用模型 |
| `GET /system/ai-model/active?modelType=` | 某能力当前生效模型 |
| `POST /system/ai-model/save` | 注册/修改 |
| `PUT /system/ai-model/active/{id}` | 设为生效 |
| `PUT /system/ai-model/enabled/{id}` | 启停 |
| `DELETE /system/ai-model/delete/{id}` | 删除 |
| `POST /system/ai-model/test/{id}` | 测试连接 |

### 8.4 设计说明与约定

- 同能力 `model_type` 内仅一条 `is_active=1`；设为生效时自动清空同能力其它 active。
- 删除/停用当前 active 模型时，active 自动让渡给同能力优先级次小的启用模型。
- 新增首条同能力模型自动置 active。
- 密钥采用 AES-GCM 加密落库（`enc:` 前缀 Base64，见 `common/util/SecretCipherUtil`），对外回显前解密后脱敏(4+****+2)；下发 Redis / 测试连接时由 `toRoute` 解密出明文供消费端使用。加密幂等 + 解密失败按明文透传，兼容历史明文存量数据。
- Redis 广播使用 String 序列化存 JSON；Hash field=model_type。消费服务通过 `ModelRouter.refreshFromRedis()` 拉取。
- **兼容旧配置回退**：当某能力未在注册表登记任何模型时，`ModelRouter` 自动回退到 `ai.llm/ai.embedding`(DashScope 原生) —— 未登记模型则原对话/向量链路完全不变，登记后才启用注册路由与故障转移。
- 消费服务(agent/knowledge)通过 `ModelRouter` 内置的懒加载(5 秒防抖)从 Redis 同步注册表，使 base-service 的"设为生效/启停"在数秒内跨服务生效。
- Ollama 需开启 `/v1` 兼容端点；DashScope 需使用 `compatible-mode/v1` 地址，方能与在线协议统一。

### 8.5 阶段三：agent / knowledge 消费端接入（已落地）

| 文件 | 说明 |
|------|------|
| `common/llm/ModelRouter` | 增加对注册表为空的"旧配置(DashScope)回退"，保证未登记模型时原链路不变；增加 5 秒防抖的 Redis 懒加载同步 |
| `knowledge/util/LlmClient` | 底层由 `DashscopeModelClient` 改走 `ModelRouter.chat`（RAG 对话生成，含本地/在线切换与故障转移） |
| `knowledge/util/EmbeddingClient` | 底层改走 `ModelRouter.embed`（FAQ/租户 RAG 向量化） |
| `agent/util/LlmUtil` | 意图识别/闲聊改走 `ModelRouter.chat` |

> 说明：knowledge 的私有化 LangChain4j/Ollama RAG 旁路、Vision/多模态等其它独立链路不属于本次对话/向量故障转移范围，保持原样。

### 8.6 阶段四：前端（已落地）

| 文件 | 说明 |
|------|------|
| `frontend/src/api/index.js` | 追加 `/system/ai-model/*` 的 8 个 API |
| `frontend/src/views/AiModelPage.vue` | 新增模型管理页：类型 Tab 筛选、注册/编辑/删除、启停开关、设为生效、测试连接、健康/供应方/能力标识、顶部"当前对话模型"下拉(快捷切换) |
| `frontend/src/router/index.js` | 新增 `/ai-model` 路由 |
| `frontend/src/App.vue` | 桌面与移动侧栏新增"AI模型管理"菜单项(icon=Cpu) |

> 密钥脱敏说明：后端查询回显 `4+****+2`；编辑时带 `*` 的密钥视为未修改(提交为空，后端保留原值)。

### 8.7 阶段五：密钥加密（已落地）

| 文件 | 说明 |
|------|------|
| `common/util/SecretCipherUtil` | AES/GCM/NoPadding 加解密工具，密文带 `enc:` 前缀(Base64(iv+密文))；加密幂等、解密失败按明文透传(兼容历史存量) |
| `base-service/.../AiModelConfigService` | 落库(save/update)前 `encryptSecret` 加密；`toRoute`/`maskSecret` 改为先解密；`api_key`/`api_secret` 列加宽到 VARCHAR(1000) |
| `base-service/application.yml` | 新增 `ai.model.secret`(${AI_MODEL_SECRET}) 可选密钥配置 |

- 密钥解析：环境变量 `AI_MODEL_SECRET` → 系统属性 `ai.model.secret` → 内置默认(仅演示/本机)；生产务必注入独立密钥。
- 数据流：MySQL 存密文 → Redis 广播/测试连接经 `toRoute` 解密为明文 → 消费端正常调用；对外查询经 `maskSecret` 先解密再脱敏(4+****+2)。
- 密钥一致性：base-service 需与消费端**不共享**加密密钥（消费端只收明文路由，无解密需求），密钥仅在 base-service 落库与读库两端使用。

### 8.8 模型增强：超时 / 熔断 / 用量（已落地）

| 文件 | 说明 |
|------|------|
| `common/llm/ModelCallResult` | 调用结果（文本 + token + 耗时） |
| `common/llm/ModelUsageEvent` | 用量事件（写 Redis 流） |
| `common/llm/ModelUsageRecorder` | 用量记录器（不阻塞主链） |
| `common/llm/ModelCircuitBreaker` | 内存熔断器（连续失败阈值 + 60s 冷却） |
| `common/llm/OpenAiCompatClient` | 支持每模型超时、解析 usage token |
| `common/llm/ModelRouter` | 接入熔断器与用量记录，chat/embed 走 `*WithUsage` |
| `base-service/entity/AiModelConfig` | 新增 timeoutMs / failThreshold / costPer1kIn / costPer1kOut |
| `base-service/entity/ModelUsageRecord` + Service/Mapper | 用量落库与查询 |
| `base-service/controller/AiModelConfigController` | 新增 `/usage/page`、`/usage/summary`、`/usage/recent-fail` |
| `job/task/ModelUsageConsumeTask` | 消费 Redis 流落库，近 5 分钟失败超阈值输出告警日志 |
| `ops/OpsAlertStore` | 接入模型失败指标，生成「模型连续失败」告警事件 |
| `frontend/AiModelPage.vue` | 注册表单新增超时/熔断/单价；新增「用量统计」页签 |

- 表结构：`cs_ai_model` 增加 `timeout_ms`、`fail_threshold`、`cost_per_1k_in`、`cost_per_1k_out`；新增 `cs_model_usage`。
- 升级脚本：`docs/database/ai_model_upgrade.sql`（已有库执行）。
- 数据流：调用 → Router 记录事件到 Redis Stream → ai-cs-job 每 5s 批量消费落库 → base-service 提供查询 → 前端展示。

### 8.9 计量与配额：重试 / 成本 / 每日上限（已落地）

| 文件 | 说明 |
|------|------|
| `cs_ai_model` | 新增 `max_retries`、`daily_token_limit`、`daily_cost_limit`（升级脚本见 `ai_model_upgrade.sql` v2 段） |
| `cs_model_usage` | 新增 `cost` 成本快照列（按发生时单价计算，改价不回溯历史） |
| `common/llm/ModelUsageRecorder` | 成功事件按单价算成本；Redis 维护当日 token/成本计数（`ai:model:usage:daily:{id}:{yyyyMMdd}`，TTL 50h）；达 80% 配额当日去重预警日志 |
| `common/llm/ModelRouter` | 同模型失败按 `maxRetries` 重试（重试不重复计熔断）；调用前检查当日配额，超限顺延候选——注册本地/免费模型为候选即"超限自动降级" |
| `base-service` | 实体/校验/汇总补新字段，`/usage/summary` 返回 `totalCost` |
| `frontend/AiModelPage.vue` | 表单新增重试次数/日 Token 配额/日成本配额；列表供应方列带「免费/按量」标识；用量页签展示单条成本与总成本 |

- 重试与熔断是两个旋钮：`maxRetries` 管单次抖动重试，`failThreshold` 管连续失败后摘除。
- 配额计数是 Redis 近似值（流消费落库与计数分离，重启不丢当日计数，TTL 50h 自动清理）。
- 租户级配额/积分体系属 SaaS 计费层，当前不做（见功能清单 §16）。

## 9. 文档同步

- 更新 `docs/功能清单.md` / `docs/模块能力.md`（标注"模型统一管理"为新增能力）
- 更新 `docs/database/init.sql`（合并 cs_ai_model）
- 更新 `docs/配置说明.md`（模型相关 yml 与 Redis 广播说明）
