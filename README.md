# AI Intelligent Customer Service System

A microservices-based intelligent customer service platform powered by Large Language Models (LLM), featuring knowledge base retrieval, work order management, and real-time communication.

## ✨ Key Features

- 🤖 **AI-Powered Chat**: Intelligent conversations using LLM with context awareness
- 📚 **Knowledge Base (RAG)**: FAQ management with vector similarity search and advanced RAG (Retrieval-Augmented Generation)
  - Multi-format document support (PDF, TXT, DOCX, MD, Excel, PPT via Apache Tika)
  - Vector embedding and Chroma/Milvus storage
  - **Rerank optimization** using BGE-Reranker for improved retrieval accuracy (20-40% boost)
  - Document version management with automatic versioning and rollback
  - Multi-user session isolation with Redis persistence
  - Learn more: [RAG Usage Guide](ai-cs-knowledge/RAG_USAGE_GUIDE.md) | [ScoringModel Extension Guide](ai-cs-knowledge/SCORING_MODEL_GUIDE.md)
- 🎫 **Work Order System**: Smart ticket creation and workflow management
- 💬 **Real-time Communication**: WebSocket-based instant messaging
- 👥 **Customer Management**: Comprehensive customer information management

## 🚀 Quick Start

Get up and running in minutes! Check out our [Quick Start Guide](docs/QUICK_START.md) for detailed setup instructions.

**Prerequisites**: JDK 17+, Maven 3.6+, MySQL 8.0+, Redis 6.0+

```bash
# Clone the repository
git clone https://gitee.com/huangrenhui/ai-cs-parent.git
cd ai-cs-parent

# Build the project
mvn clean install -DskipTests

# Start services (see Quick Start Guide for details)
```

## 📚 Documentation

Comprehensive documentation is available in the [docs](docs/) directory:

- 📘 [Quick Start Guide](docs/QUICK_START.md) - Get started quickly
- 🏗️ [Architecture Overview](docs/architecture/overview.md) - System architecture and design
- 📦 [Module Details](docs/architecture/modules.md) - Module responsibilities and interfaces
- 🔄 [Data Flow](docs/architecture/data-flow.md) - Business processes and data flows
- ⚙️ [Configuration Guide](docs/deployment/configuration.md) - Configuration management
- 🗄️ [Database Migration](docs/database/migration.md) - Database schema changes
- 🧪 [Testing Guide](docs/development/testing.md) - Testing strategies and practices
- 📖 [Development Standards](docs/development/standards.md) - Code conventions

For a complete list of documentation, see [Docs Index](docs/README.md).

## 🛠️ Tech Stack

**Backend**:
- Java 17 + Spring Boot 3.x
- Spring Cloud Gateway (Microservices)
- MyBatis Plus + MySQL 8.0
- Redis 6.0+ (Caching)
- Milvus 2.x (Vector Database)

**Frontend**:
- Vue 3 + Vite
- Element Plus UI
- Axios + Vue Router

**AI/ML**:
- LLM Integration (Qwen, ChatGLM, etc.)
- RAG (Retrieval-Augmented Generation) with LangChain4j
- Embedding Services (Ollama, nomic-embed-text)
- Vector Database (Chroma, Milvus)
- Reranking Models (BGE-Reranker)

## 📁 Project Structure

```
ai-cs-parent/
├── ai-cs-common/          # Shared utilities and common code
├── ai-cs-api/             # Feign client interfaces
├── ai-cs-gateway/         # API Gateway (port 8080)
├── ai-cs-base-service/    # Customer & session management (port 9001)
├── ai-cs-ai-agent/        # AI chat service (port 9002)
├── ai-cs-knowledge/       # Knowledge base & vector search (port 9003)
├── ai-cs-workorder/       # Work order management (port 9004)
├── ai-cs-websocket/       # Real-time communication (port 9005)
├── ai-cs-job/             # Scheduled tasks
├── ai-cs-frontend/        # Vue.js frontend application
└── docs/                  # Documentation
```

## 🤝 Contributing

We welcome contributions! Please see our [Contributing Guidelines](docs/development/standards.md) for details.

1. Fork the repository
2. Create a feature branch: `git checkout -b feature/amazing-feature`
3. Commit your changes: `git commit -m 'Add amazing feature'`
4. Push to the branch: `git push origin feature/amazing-feature`
5. Open a Pull Request

## 📄 License

This project is licensed under the [MIT License](LICENSE).

## 👥 Contact

- **Maintainer**: huangrenhui
- **Gitee**: https://gitee.com/huangrenhui/ai-cs-parent
- **Issues**: Please report bugs via Gitee Issues

## 📊 Project Status

🟢 Actively Maintained

---

**Note**: This is the English version. For Chinese documentation, see [README_CN.md](README_CN.md).
