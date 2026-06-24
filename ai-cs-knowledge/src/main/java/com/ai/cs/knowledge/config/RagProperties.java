package com.ai.cs.knowledge.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * RAG配置属性类
 * 从application.yml读取rag前缀的配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "rag")
public class RagProperties {

    private Ollama ollama;
    private Chroma chroma;
    private Split split;
    private Retrieve retrieve;
    private ChatMemory chatMemory;

    @Data
    public static class Ollama {
        private String baseUrl;
        private String llmModel;
        private String embeddingModel;
        private Double temperature;
    }

    @Data
    public static class Chroma {
        private String baseUrl;
        private String collectionName;
        private String persistPath;
    }

    @Data
    public static class Split {
        private Integer chunkSize;
        private Integer chunkOverlap;
    }

    @Data
    public static class Retrieve {
        private Integer topK;
    }

    @Data
    public static class ChatMemory {
        private Long ttl;
    }
}
