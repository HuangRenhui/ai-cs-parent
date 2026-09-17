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

    /** Ollama模型服务配置 */
    private Ollama ollama;
    /** Chroma向量库配置 */
    private Chroma chroma;
    /** 文档切片配置 */
    private Split split;
    /** 检索召回配置 */
    private Retrieve retrieve;
    /** 对话记忆配置 */
    private ChatMemory chatMemory;

    /**
     * Ollama模型服务配置
     */
    @Data
    public static class Ollama {
        /** Ollama服务地址 */
        private String baseUrl;
        /** 对话大模型名称 */
        private String llmModel;
        /** Embedding向量模型名称 */
        private String embeddingModel;
        /** Rerank重排模型名称 */
        private String rerankModel;
        /** 采样温度，越低输出越稳定 */
        private Double temperature;
    }

    /**
     * Chroma向量库配置
     */
    @Data
    public static class Chroma {
        /** Chroma服务地址 */
        private String baseUrl;
        /** 向量集合名称 */
        private String collectionName;
        /** 持久化存储路径 */
        private String persistPath;
    }

    /**
     * 文档切片配置
     */
    @Data
    public static class Split {
        /** 分块大小（字符数） */
        private Integer chunkSize = 500;
        /** 相邻分块重叠字符数，避免语义在边界处被截断 */
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

    /**
     * 检索召回配置
     */
    @Data
    public static class Retrieve {
        /** 向量检索召回的Top结果数 */
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

    /**
     * 对话记忆配置
     */
    @Data
    public static class ChatMemory {
        /** 对话记忆在Redis中的过期时间（秒） */
        private Long ttl;
    }
}
