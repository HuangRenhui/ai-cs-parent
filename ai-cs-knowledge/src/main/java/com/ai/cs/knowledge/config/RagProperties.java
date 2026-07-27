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
        private String rerankModel;  // Rerank重排模型名称
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
        private Integer chunkSize = 500;
        private Integer chunkOverlap = 80;
        // ===== 语义切片优化 =====
        private Boolean semanticSplitEnabled = false;  // 是否启用语义切片（按段落/标题边界分割）
        private String semanticSeparators = "\n\n,。,；;！!？?,.，";  // 语义分隔符（优先级从高到低）
        // ===== 分层切片优化 =====
        private Boolean hierarchicalSplitEnabled = false;  // 是否启用分层切片（文档→段落→句子→词语）
        private Integer paragraphMaxLength = 2000;    // 段落最大长度（字符）
        private Integer sentenceMaxLength = 500;      // 句子最大长度（字符）
        private Integer minChunkLength = 50;          // 最小分块长度（太短的丢弃）
    }

    @Data
    public static class Retrieve {
        private Integer topK = 5;
        private Integer rerankTopK = 3;  // Rerank重排后保留的结果数量
        private Double minScore = 0.6;   // Rerank最低相关性阈值
        // ===== 召回过滤优化 =====
        private Double vectorMinScore = 0.5;   // 向量检索最低相似度阈值（初筛）
        private Double bm25MinScore = 0.3;     // BM25关键词最低分数阈值
        private Integer maxRecallDocs = 10;    // 最大召回文档数量（控制上下文长度）
        // ===== 文档质量过滤 =====
        private Boolean qualityFilterEnabled = true;    // 是否启用品质量过滤
        private Integer minContentLength = 20;          // 文档内容最小长度（太短视为低质）
        private Double maxDuplicateRatio = 0.8;         // 最大重复率（超过视为冗余）
        // ===== 上下文压缩 =====
        private Boolean summaryCompressEnabled = true;  // 是否启用摘要压缩
        private Integer maxContextLength = 4000;        // 上下文最大长度（字符），超过则压缩
        private Integer compressTargetLength = 2000;    // 压缩目标长度（字符）
    }

    @Data
    public static class ChatMemory {
        private Long ttl;
    }
}
