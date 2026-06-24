# AI 智能客服系统

基于微服务架构的智能客服平台，集成大语言模型（LLM）、知识库检索、工单管理等功能，为企业提供智能化客户服务解决方案。

## ✨ 核心特性

- 🤖 **AI 智能对话**：基于 LLM 的智能问答，支持上下文会话管理
- 📚 **知识库检索 (RAG)**：FAQ 管理 + 向量相似度搜索 + 高级 RAG（检索增强生成）
  - 多格式文档支持（PDF、TXT、DOCX、MD）
  - 向量化存储与检索（Chroma/Milvus）
  - Rerank 重排优化，提升检索精度
  - 多用户会话隔离，Redis 持久化存储
  - **文档版本管理**：支持文档版本跟踪、回退和对比
- 🎫 **工单系统**：智能工单创建与流转管理
- 💬 **实时通信**：基于 WebSocket 的即时消息推送
- 👥 **客户管理**：完整的客户信息管理系统

## 🚀 快速开始

几分钟内即可运行！详细启动步骤请查看 [快速开始指南](docs/QUICK_START.md)。

**环境要求**: JDK 17+, Maven 3.6+, MySQL 8.0+, Redis 6.0+

```bash
# 克隆项目
git clone https://gitlab.com/WongHui/ai-cs-parent.git
cd ai-cs-parent

# 编译项目
mvn clean install -DskipTests

# 启动服务（详见快速开始指南）
```

## 📚 文档导航

完整文档位于 [docs](docs/) 目录：

- 📘 [快速开始指南](docs/QUICK_START.md) - 快速上手
- 🏗️ [架构概览](docs/architecture/overview.md) - 系统架构设计
- 📦 [模块说明](docs/architecture/modules.md) - 模块职责与接口
- 🔄 [数据流向](docs/architecture/data-flow.md) - 业务流程与数据流
- ⚙️ [配置管理](docs/deployment/configuration.md) - 配置详解
- 🗄️ [数据库迁移](docs/database/migration.md) - Schema 变更管理
- 🧪 [测试指南](docs/development/testing.md) - 测试策略与实践
- 📖 [开发规范](docs/development/standards.md) - 代码规范

更多文档请查看 [文档索引](docs/README.md)。

## 🛠️ 技术栈

**后端技术**：
- Java 17 + Spring Boot 3.x
- Spring Cloud Gateway (微服务)
- MyBatis Plus + MySQL 8.0
- Redis 6.0+ (缓存)
- Milvus 2.x (向量数据库)

**前端技术**：
- Vue 3 + Vite
- Element Plus UI
- Axios + Vue Router

**AI/ML**：
- LLM 集成 (通义千问、ChatGLM 等)
- RAG (检索增强生成) 基于 LangChain4j
- Embedding 向量化服务 (Ollama, nomic-embed-text)
- 向量数据库 (Chroma, Milvus)
- 重排模型 (BGE-Reranker)

## 📁 项目结构

```
ai-cs-parent/
├── ai-cs-common/          # 公共模块 - 工具类、常量、DTO
├── ai-cs-api/             # API 模块 - Feign 接口定义
├── ai-cs-gateway/         # 网关服务 (端口 8080)
├── ai-cs-base-service/    # 基础服务 - 客户管理 (端口 9001)
├── ai-cs-ai-agent/        # AI 代理服务 - 智能对话 (端口 9002)
├── ai-cs-knowledge/       # 知识库服务 - 向量检索 (端口 9003)
├── ai-cs-workorder/       # 工单服务 - 工单管理 (端口 9004)
├── ai-cs-websocket/       # WebSocket 服务 - 实时通信 (端口 9005)
├── ai-cs-job/             # 定时任务服务
├── ai-cs-frontend/        # Vue.js 前端应用
└── docs/                  # 项目文档
```

## 🤝 贡献指南

欢迎贡献代码！详情请查看 [开发规范](docs/development/standards.md)。

1. Fork 本仓库
2. 创建特性分支: `git checkout -b feature/amazing-feature`
3. 提交更改: `git commit -m 'Add amazing feature'`
4. 推送到分支: `git push origin feature/amazing-feature`
5. 开启 Pull Request

## 📄 许可证

本项目采用 [MIT 许可证](LICENSE)。

## 👥 联系方式

- **维护者**: Wong Hui
- **GitLab**: https://gitlab.com/WongHui/ai-cs-parent
- **问题反馈**: 请通过 GitLab Issues 提交

## 📊 项目状态

🟢 持续维护中

---

**注意**: 这是中文版本。英文文档请查看 [README.md](README.md)。
