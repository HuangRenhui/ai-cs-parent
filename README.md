# AI Intelligent Customer Service (Zhiyu)

> [English](README.md) · [中文](README_CN.md)

A microservices-based intelligent customer service platform powered by Large Language Models (LLMs), Knowledge Retrieval (RAG), ticket workflows, and real-time WebSocket messaging.

## ✨ Key Features

- 🤖 **AI-Powered Conversations**: LLM-driven intelligent Q&A with context-aware session management
- 📚 **Knowledge Base with RAG**: FAQ management + vector similarity search + advanced Retrieval-Augmented Generation
  - Multi-format document support (PDF, TXT, DOCX, MD)
  - Vectorized storage and retrieval (Chroma / Milvus)
  - Reranking optimization for improved retrieval precision
  - Multi-user session isolation with Redis persistence
  - See [Knowledge Base Guide](docs/知识库手册.md) for details
- 🎫 **Ticket System**: Smart ticket creation and lifecycle management
- 💬 **Real-Time Communication**: WebSocket-based instant message delivery
- 👥 **Customer Management**: Complete customer information management system

## 🚀 Quick Start

Up and running in minutes! See [Quick Start](docs/快速开始.md) for the detailed guide.

**Prerequisites**: JDK 17+, Maven 3.6+, MySQL 8.0+, Redis 6.0+

```bash
# Clone the repository
git clone https://gitee.com/huangrenhui/ai-cs-parent.git
cd ai-cs-parent

# Build the project
mvn clean install -DskipTests

# Start services (see Quick Start for full details)
```

## 📚 Documentation

All documentation lives under [docs/](docs/):

- 📘 [Documentation Index](docs/文档说明.md) — category-based guide
- 🎯 [Feature List](docs/功能清单.md) — implementation roadmap (aligned with §17-B)
- 🗺️ [Project Status](docs/项目现状.md) — code-to-spec mapping
- 🚀 [Quick Start](docs/快速开始.md) — run locally
- 🏗️ [System Architecture](docs/系统架构.md) — microservice topology
- ⚙️ [Configuration](docs/配置说明.md) — environment and service config
- 🧩 [Modules](docs/模块说明.md) — service-by-service breakdown
- 📝 [API Reference](docs/接口说明.md) — interface documentation
- 🗄️ [Database Design](docs/数据库设计.md) — schema and init scripts
- 🔧 [Coding Standards](docs/编码规范.md) — contribution guidelines
- 🐳 [Docker Deployment](docs/Docker部署.md) — containerized setup
- 📦 [Manual Deployment](docs/手动部署.md) — bare-metal setup
- 🧪 [Testing Guide](docs/测试指南.md) — how to test

## 🛠️ Tech Stack

**Backend**:
- Java 17 + Spring Boot 3.2
- Spring Cloud 2023.0 + Spring Cloud Alibaba 2023.0.1
- Spring Cloud Gateway (API gateway)
- MyBatis-Plus 3.5 + MySQL 8.0
- Redis 6.0+ (cache / session store)
- Milvus 2.x (vector database)
- Resilience4j (circuit breaker & rate limiting)
- Flowable 7.0 (workflow engine)
- Knife4j (OpenAPI / Swagger UI)
- JJWT (authentication)

**AI / ML**:
- LangChain4j 0.32 (RAG framework)
- Ollama (local LLM + embeddings)
- DashScope / OpenAI-compatible / DeepSeek (cloud LLM providers)
- Chroma / Milvus (vector stores)
- BGE-Reranker (reranking)

**Frontend**:
- Vue 3 + Vite
- Element Plus UI
- Axios + Vue Router

**Observability**:
- Micrometer + Prometheus (metrics)

## 📁 Project Structure

```
ai-cs-parent/
├── ai-cs-common/          # Common module — utilities, constants, DTOs
├── ai-cs-api/             # API module — Feign interface definitions
├── ai-cs-gateway/         # API Gateway (port 8080)
├── ai-cs-websocket/       # WebSocket — real-time messaging (port 8081)
├── ai-cs-agent/           # AI Agent — intelligent conversations (port 8082)
├── ai-cs-knowledge/        # Knowledge Base — vector retrieval (port 8083)
├── ai-cs-base-service/    # Base Service — customer management (port 8084)
├── ai-cs-workorder/       # Work Order — ticket management (port 8085)
├── ai-cs-open/            # Open Platform — connectors & tools (port 8086)
├── ai-cs-ops/             # Ops Console (port 8087)
├── ai-cs-job/             # Scheduled Jobs (port 8088)
├── ai-cs-frontend/        # Vue 3 + Vite web app (standalone, not in Maven modules)
├── docs/                  # Documentation (start with docs/文档说明.md)
├── README.md              # This file
├── README_CN.md           # Chinese version
├── pom.xml                # Maven parent POM (backend modules only)
└── .gitignore
```

## 🤝 Contributing

Contributions are welcome! Please read the [Coding Standards](docs/编码规范.md) first.

1. Fork the repo
2. Create your feature branch: `git checkout -b feature/amazing-feature`
3. Commit your changes: `git commit -m 'Add amazing feature'`
4. Push to the branch: `git push origin feature/amazing-feature`
5. Open a Pull Request

## 📄 License

This project is licensed under the [MIT License](LICENSE).

## 👥 Contact

- **Maintainer**: huangrenhui
- **Gitee**: https://gitee.com/huangrenhui/ai-cs-parent
- **Issues**: Please report via Gitee Issues

## 📊 Project Status

🟢 Actively maintained

---

[⬆️ Back to top](#ai-intelligent-customer-service-zhiyu) · [中文版 README](README_CN.md)