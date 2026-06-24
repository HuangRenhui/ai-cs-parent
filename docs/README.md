# 项目文档中心

欢迎查阅 AI 智能客服系统的完整技术文档！

## 🚀 快速入口

- **新手入门**: [快速开始指南](QUICK_START.md) - 5分钟上手
- **架构设计**: [系统架构概览](architecture/overview.md)
- **开发指南**: [开发规范](development/standards.md)
- **部署运维**: [配置管理](deployment/configuration.md)

---

## 📚 文档分类

### 🏗️ 架构文档 (Architecture)

了解系统整体设计和模块划分。

- [系统架构概览](architecture/overview.md) - 架构图、技术栈、部署架构
- [模块详细说明](architecture/modules.md) - 各模块职责、接口定义
- [数据流向与业务场景](architecture/data-flow.md) - 核心业务流程
- **[RAG技术决策](architecture/RAG_TECHNICAL_DECISION.md)** ⭐⭐ - 两套RAG方案对比、优缺点分析及演进方向
- **[Agent vs Knowledge对比](architecture/AGENT_VS_KNOWLEDGE_COMPARISON.md)** ⭐⭐ - ai-cs-agent与ai-cs-knowledge功能边界、技术差异及协作方式

### 💻 开发文档 (Development)

开发人员必读文档。

- [代码规范](development/standards.md) - 编码约定、最佳实践
- [开发指南](development/guide.md) - 环境搭建、开发流程
- [测试指南](development/testing.md) - 单元测试、集成测试

### 🚢 部署文档 (Deployment)

部署和运维相关文档。

- [配置管理指南](deployment/configuration.md) - 配置详解、多环境管理
- [Docker 部署](deployment/docker.md) - 容器化部署
- [手动部署](deployment/manual.md) - 传统部署方式

### 🗄️ 数据库文档 (Database)

数据库设计和迁移相关文档。

- [数据库设计](database/schema.md) - 表结构、ER 图
- [初始化脚本](database/init.sql) - 完整建表脚本
- [迁移指南](database/migration.md) - Schema 变更管理

### 🤖 AI & RAG 文档 (AI & RAG)

RAG（检索增强生成）系统相关文档。

- **[RAG使用指南](../ai-cs-knowledge/RAG_USAGE_GUIDE.md)** ⭐ - 完整的功能说明、API文档和Rerank重排详解
- **[ScoringModel扩展实践](../ai-cs-knowledge/SCORING_MODEL_GUIDE.md)** ⭐ - 如何扩展自定义评分模型（Jina/Cohere/ONNX）

### 🔌 API 文档 (API)

接口文档。

- [API 接口说明](api/README.md) - RESTful API 文档

---

## 📖 推荐阅读路径

### 新手开发者

1. [快速开始指南](QUICK_START.md) - 运行项目
2. [系统架构概览](architecture/overview.md) - 了解整体设计
3. [模块详细说明](architecture/modules.md) - 熟悉各模块功能
4. [代码规范](development/standards.md) - 开始编码

### AI/RAG 开发者

1. [RAG使用指南](../ai-cs-knowledge/RAG_USAGE_GUIDE.md) - RAG功能介绍和API文档
2. [RAG技术决策](architecture/RAG_TECHNICAL_DECISION.md) - 两套RAG方案对比
3. [快速开始指南](QUICK_START.md) - 启动前置服务
4. [模块详细说明](architecture/modules.md) - ai-cs-knowledge 模块

### 运维工程师

1. [配置管理指南](deployment/configuration.md) - 理解配置项
2. [Docker 部署](deployment/docker.md) - 容器化部署
3. [数据库迁移](database/migration.md) - 数据库维护

### 架构师

1. [系统架构概览](architecture/overview.md) - 架构设计
2. [数据流向与业务场景](architecture/data-flow.md) - 业务流程
3. [配置管理指南](deployment/configuration.md) - 技术选型

---

## 🔍 查找文档

### 按主题查找

| 我想... | 查看文档 |
|---------|----------|
| 快速运行项目 | [快速开始指南](QUICK_START.md) |
| 了解系统架构 | [架构概览](architecture/overview.md) |
| 了解RAG技术方案 | **[RAG技术决策](architecture/RAG_TECHNICAL_DECISION.md)** |
| 了解Agent和Knowledge区别 | **[Agent vs Knowledge对比](architecture/AGENT_VS_KNOWLEDGE_COMPARISON.md)** |
| 添加新功能 | [开发指南](development/guide.md) |
| 编写测试 | [测试指南](development/testing.md) |
| 配置环境 | [配置管理](deployment/configuration.md) |
| 修改数据库 | [迁移指南](database/migration.md) |
| 调用 API | [API 文档](api/README.md) |
| 使用 RAG 功能 | **[RAG使用指南](../ai-cs-knowledge/RAG_USAGE_GUIDE.md)** |
| 扩展 ScoringModel | **[ScoringModel扩展实践](../ai-cs-knowledge/SCORING_MODEL_GUIDE.md)** |

### 按角色查找

| 角色 | 重点文档 |
|------|----------|
| 前端开发 | API 文档、架构模块说明、Agent vs Knowledge对比 |
| 后端开发 | 开发指南、测试指南、数据库文档、Agent vs Knowledge对比 |
| 测试工程师 | 测试指南、API 文档 |
| 运维工程师 | 配置管理、部署文档、数据库迁移 |
| 产品经理 | 架构概览、数据流向、功能说明、Agent vs Knowledge对比 |
| AI 工程师 | RAG使用指南、RAG技术决策、Agent vs Knowledge对比 |

---

## 📝 文档维护

### 更新记录

- **2026-06-24**: 新增RAG技术决策文档，详细记录两套RAG方案（Milvus传统RAG vs LangChain4j RAG）的对比分析、优缺点、使用场景及融合优化方案；完成Rerank重排功能实现，新增OllamaScoringModel、ScoringModel扩展指南、Rerank实现总结和快速参考文档；新增文档版本管理功能，包括版本跟踪、回退、对比等功能；**模块重命名：ai-cs-ai-agent → ai-cs-agent**，修复所有引用；新增Agent与Knowledge功能对比文档，明确两个模块的职责边界和协作方式
- **2026-06-23**: 新增 RAG 系统文档，包括 RAG 功能概览和架构设计
- **2024-06-20**: 文档重构完成，新增快速开始、配置管理、测试指南、开发指南、Docker 部署、手动部署、API 文档等完整文档体系
- 更多历史记录请查看 Git 提交日志

### 贡献文档

欢迎改进文档！

1. 发现文档错误？→ 提交 Issue
2. 想补充内容？→ 提交 Pull Request
3. 有疑问？→ 联系维护者

### 文档规范

- 使用 Markdown 格式
- 文件名使用小写+下划线
- 保持文档与代码同步
- 提供清晰的示例和截图

---

## 🔗 相关链接

- [项目主页](../README_CN.md) - 中文 README
- [Project Home](../README.md) - English README
- [Gitee 仓库](https://gitee.com/huangrenhui/ai-cs-parent)
- [问题反馈](https://gitee.com/huangrenhui/ai-cs-parent/issues)

---

**需要帮助？** 查看 [快速开始指南](QUICK_START.md) 或提交 Issue。
