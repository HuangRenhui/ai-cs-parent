# AI Intelligent Customer Service System (ai-cs-parent)

## Project Overview

AI Intelligent Customer Service System is a microservices architecture project built on Spring Boot 3.x, integrating Large Language Models (LLM), knowledge base retrieval, work order management, and other features to provide enterprises with intelligent customer service solutions.

## Tech Stack

- **Backend Framework**: Spring Boot 3.x + Java 17
- **Microservices Architecture**: Spring Cloud Gateway
- **Database**: MySQL + MyBatis Plus
- **Vector Database**: Milvus
- **Cache**: Redis
- **Communication Protocol**: WebSocket
- **AI Capabilities**: LLM Integration + RAG Knowledge Retrieval
- **Build Tool**: Maven

## Project Structure

```
ai-cs-parent/
├── ai-cs-common/          # Common Module - Utilities, Constants, DTOs, Exception Handling
├── ai-cs-api/             # API Module - Feign Client Interface Definitions
├── ai-cs-gateway/         # Gateway Module - Unified Entry Point, Route Forwarding
├── ai-cs-base-service/    # Base Service - Customer Management, Session Management
├── ai-cs-ai-agent/        # AI Agent Module - LLM Chat Service
├── ai-cs-knowledge/       # Knowledge Base Module - FAQ Management, Vector Retrieval
├── ai-cs-workorder/       # Work Order Module - Work Order Creation and Management
├── ai-cs-websocket/       # WebSocket Module - Real-time Communication
└── ai-cs-job/             # Scheduled Task Module
```

## Core Features

### 1. AI Intelligent Chat
- Intelligent Q&A based on Large Language Models
- Support for multiple LLM model configurations
- Context session management

### 2. Knowledge Base Retrieval (RAG)
- FAQ knowledge base management
- Vector similarity search
- Embedding vectorization service

### 3. Customer Service
- Customer information management
- Chat session management
- Real-time message push (WebSocket)

### 4. Work Order System
- Intelligent work order creation
- Work order workflow management
- Issue tracking and handling

## Environment Requirements

- JDK 17+
- Maven 3.6+
- MySQL 8.0+
- Redis 6.0+
- Milvus 2.x (optional, for vector retrieval)

## Quick Start

### 1. Clone the Project

```bash
git clone https://gitlab.com/WongHui/ai-cs-parent.git
cd ai-cs-parent
```

### 2. Configure Environment Variables

Configure the following key parameters in each module's `application.yml`:

**AI Model Configuration** (`ai-cs-ai-agent`):
```yaml
llm:
  url: http://127.0.0.1:8000/v1/chat/completions
  api-key: xxx
  model: qwen-7b
  temperature: 0.2
```

**Embedding Service Configuration** (`ai-cs-knowledge`):
```yaml
embedding:
  url: http://127.0.0.1:8000/embedding
```

**Database Configuration** (each service module):
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/ai_cs_db?useUnicode=true&characterEncoding=utf8
    username: root
    password: your_password
```

### 3. Build the Project

```bash
mvn clean install
```

### 4. Start Services

Start each microservice in the following order:

1. **Gateway Service**: `ai-cs-gateway`
2. **Base Service**: `ai-cs-base-service`
3. **AI Agent Service**: `ai-cs-ai-agent`
4. **Knowledge Base Service**: `ai-cs-knowledge`
5. **Work Order Service**: `ai-cs-workorder`
6. **WebSocket Service**: `ai-cs-websocket`

```bash
# Example: Start gateway service
cd ai-cs-gateway
mvn spring-boot:run
```

### 5. Access the Service

- **Gateway Address**: http://localhost:8080
- **WebSocket Connection**: ws://localhost:8081/ws

## Configuration Guide

### Configurable Items

| Configuration Item | Default Value | Description |
|-------------------|---------------|-------------|
| `llm.url` | http://127.0.0.1:8000/v1/chat/completions | LLM service address |
| `llm.api-key` | xxx | API key |
| `llm.model` | qwen-7b | Model name |
| `llm.temperature` | 0.2 | Temperature parameter |
| `embedding.url` | http://127.0.0.1:8000/embedding | Embedding service address |

## Development Guide

### Code Standards

- Use camelCase for variable naming
- Comments in English, Chinese allowed for special business logic
- Follow Spring Boot best practices

### Adding New Modules

1. Add new module in parent POM
2. Inherit `ai-cs-common` common dependencies
3. Configure independent `application.yml`
4. Configure routing rules in gateway

### Testing

```bash
# Run all tests
mvn test

# Run tests for a specific module
cd ai-cs-ai-agent
mvn test
```

## Deployment

### Docker Deployment (Recommended)

Create Dockerfile for each microservice and use docker-compose for orchestration:

```yaml
version: '3'
services:
  gateway:
    build: ./ai-cs-gateway
    ports:
      - "8080:8080"
  
  base-service:
    build: ./ai-cs-base-service
    depends_on:
      - mysql
      - redis
  
  # ... other services
```

### Traditional Deployment

1. Package each module: `mvn clean package`
2. Upload JAR files to server
3. Start using script: `java -jar xxx.jar`

## Frontend Module Startup

### Project Structure

```
ai-cs-frontend/
├── package.json          # Dependency configuration
├── vite.config.js        # Vite build configuration
├── index.html            # Entry HTML
└── src/
    ├── main.js           # Application entry point
    ├── App.vue           # Main component (sidebar navigation)
    ├── router/
    │   └── index.js      # Route configuration
    ├── utils/
    │   └── request.js    # Unified request wrapper
    └── views/
        ├── ChatPage.vue      # AI Chat page
        ├── CustomerPage.vue  # Customer management page
        ├── WorkOrderPage.vue # Work order management page
        └── KnowledgePage.vue # Knowledge base management page
```

### Startup Steps

#### 1. Enter Frontend Directory

```bash
cd ai-cs-frontend
```

#### 2. Install Dependencies

```bash
npm install
```

#### 3. Start Development Server

```bash
npm run dev
```

#### 4. Build Production Version

```bash
npm run build
```

### Access Address

The frontend service runs by default at `http://localhost:8080`, with `/api` prefix proxy forwarding to backend service `http://localhost:9000`.

### Page Features

| Page | Path | Function |
|------|------|----------|
| AI Chat | /chat | Real-time conversation with AI customer service |
| Customer Management | /customer | Customer list display, add new customers |
| Work Order Management | /workorder | Create work orders |
| Knowledge Base Management | /knowledge | FAQ management, semantic search, vectorization operations |

## FAQ

### 1. Maven Build Warnings

Warnings may appear when using Java 17, which is normal and does not affect the build result.

### 2. Dependency Conflicts

Ensure that dependency management in parent POM uses `<dependencyManagement>` tag.

### 3. Jakarta EE Migration

Spring Boot 3.x has changed `javax.annotation` to `jakarta.annotation`, please check import statements.

## Contributing

Issues and Pull Requests are welcome!

1. Fork this repository
2. Create a feature branch: `git checkout -b feature/AmazingFeature`
3. Commit your changes: `git commit -m 'Add some AmazingFeature'`
4. Push to the branch: `git push origin feature/AmazingFeature`
5. Open a Pull Request

## License

This project is licensed under the [MIT License](LICENSE)

## Contact

- Project Maintainer: Wong Hui
- GitLab: https://gitlab.com/WongHui/ai-cs-parent

## Project Status

🟢 Under Active Development

---

**Note**: This is the English version of README. For Chinese documentation, please refer to README_CN.md.
