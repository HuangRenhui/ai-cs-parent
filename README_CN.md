# AI 智能客服系统 (智语)

> [English](README.md) · [中文](README_CN.md)

基于微服务架构的智能客服平台，集成大语言模型（LLM）、知识库检索、工单管理等功能，为企业提供智能化客户服务解决方案。

## ✨ 核心特性

- 🤖 **AI 智能对话**：基于 LLM 的智能问答，支持上下文会话管理
- 📚 **知识库检索 (RAG)**：FAQ 管理 + 向量相似度搜索 + 高级 RAG（检索增强生成）
  - 多格式文档支持（PDF、TXT、DOCX、MD）
  - 向量化存储与检索（Chroma/Milvus）
  - Rerank 重排优化，提升检索精度
  - 多用户会话隔离，Redis 持久化存储
  - 详见 [知识库手册](docs/知识库手册.md)
- 🎫 **工单系统**：智能工单创建与流转管理
- 💬 **实时通信**：基于 WebSocket 的即时消息推送
- 👥 **客户管理**：完整的客户信息管理系统

## 🚀 快速开始

几分钟内即可运行！详细启动步骤请查看 [快速开始](docs/快速开始.md)。

**环境要求**: JDK 17+, Maven 3.6+, MySQL 8.0+, Redis 6.0+

```bash
# 克隆项目
git clone https://gitee.com/huangrenhui/ai-cs-parent.git
cd ai-cs-parent

# 编译项目
mvn clean install -DskipTests

# 启动服务（详见快速开始）
```

## 📚 文档导航

完整文档位于 [docs](docs/) 目录：

- 📘 [文档说明](docs/文档说明.md) — 分类索引
- 🎯 [功能清单](docs/功能清单.md) — 实现目标（按 §17-B）
- 🗺️ [项目现状](docs/项目现状.md) — 代码对照
- 🚀 [快速开始](docs/快速开始.md) — 本地启动
- 🏗️ [系统架构](docs/系统架构.md) — 微服务拓扑
- ⚙️ [配置说明](docs/配置说明.md) — 环境与服务配置
- 🧩 [模块说明](docs/模块说明.md) — 各服务详解
- 📝 [接口说明](docs/接口说明.md) — API 文档
- 🗄️ [数据库设计](docs/数据库设计.md) — 表结构与初始化
- 🔧 [编码规范](docs/编码规范.md) — 贡献指南
- 🐳 [Docker 部署](docs/Docker部署.md) — 容器化部署
- 📦 [手动部署](docs/手动部署.md) — 裸机部署
- 🧪 [测试指南](docs/测试指南.md) — 测试说明

## 🛠️ 技术栈

**后端技术**：
- Java 17 + Spring Boot 3.2
- Spring Cloud 2023.0 + Spring Cloud Alibaba 2023.0.1
- Spring Cloud Gateway（API 网关）
- MyBatis-Plus 3.5 + MySQL 8.0
- Redis 6.0+（缓存 / 会话存储）
- Milvus 2.x（向量数据库）
- Resilience4j（熔断限流）
- Flowable 7.0（工作流引擎）
- Knife4j（OpenAPI / Swagger UI）
- JJWT（身份认证）

**前端技术**：
- Vue 3 + Vite
- Element Plus UI
- Axios + Vue Router

**AI / ML**：
- LangChain4j 0.32（RAG 框架）
- Ollama（本地 LLM + Embedding）
- DashScope / OpenAI 兼容协议 / DeepSeek（云端 LLM）
- Chroma / Milvus（向量存储）
- BGE-Reranker（重排）

**可观测性**：
- Micrometer + Prometheus（指标采集）

## 📁 项目结构

```
ai-cs-parent/
├── ai-cs-common/          # 公共模块 - 工具类、常量、DTO
├── ai-cs-api/             # API 模块 - Feign 接口定义
├── ai-cs-gateway/         # 网关服务 (端口 8080)
├── ai-cs-websocket/       # WebSocket 服务 - 实时通信 (端口 8081)
├── ai-cs-agent/           # AI 代理服务 - 智能对话 (端口 8082)
├── ai-cs-knowledge/       # 知识库服务 - 向量检索 (端口 8083)
├── ai-cs-base-service/    # 基础服务 - 客户管理 (端口 8084)
├── ai-cs-workorder/       # 工单服务 - 工单管理 (端口 8085)
├── ai-cs-open/            # 开放接入 - 连接器/工具 (端口 8086)
├── ai-cs-ops/             # 运维控制台 (端口 8087)
├── ai-cs-job/             # 定时任务服务 (端口 8088)
├── ai-cs-frontend/        # Vue 3 + Vite 前端应用（独立工程，不在 Maven modules 中）
├── docs/                  # 文档（入口：docs/文档说明.md）
├── README.md              # 英文说明
├── README_CN.md           # 中文说明
├── pom.xml                # Maven 父 POM
└── .gitignore
```

## 🤝 贡献指南

欢迎贡献代码！详情请查看 [开发规范](docs/编码规范.md)。

1. Fork 本仓库
2. 创建特性分支: `git checkout -b feature/amazing-feature`
3. 提交更改: `git commit -m 'Add amazing feature'`
4. 推送到分支: `git push origin feature/amazing-feature`
5. 开启 Pull Request

## 📄 许可证

本项目采用 [MIT 许可证](LICENSE)。

## 👥 联系方式

- **维护者**: huangrenhui
- **Gitee**: https://gitee.com/huangrenhui/ai-cs-parent
- **问题反馈**: 请通过 Gitee Issues 提交

## 📊 项目状态

🟢 持续维护中

---

[⬆️ 回到顶部](#ai-智能客服系统-智语) · [English README](README.md)