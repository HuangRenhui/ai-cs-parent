package com.ai.cs.knowledge.config;

import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.scoring.ScoringModel;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.aggregator.ReRankingContentAggregator;
import dev.langchain4j.rag.content.aggregator.ContentAggregator;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.chroma.ChromaEmbeddingStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * LangChain4j核心配置类
 * 负责装配大模型、向量库、检索增强器、对话记忆等组件
 */
@Configuration
public class LangChainConfig {

    private final RagProperties ragProperties;
    private final RedisChatMemoryStore redisChatMemoryStore;
    private final int MAX_HISTORY_MSG = 10;

    public LangChainConfig(RagProperties ragProperties, RedisChatMemoryStore redisChatMemoryStore) {
        this.ragProperties = ragProperties;
        this.redisChatMemoryStore = redisChatMemoryStore;
    }

    /**
     * 构建Ollama对话大模型Bean
     */
    @Bean
    public ChatLanguageModel chatLanguageModel() {
        return OllamaChatModel.builder()
                .baseUrl(ragProperties.getOllama().getBaseUrl())
                .modelName(ragProperties.getOllama().getLlmModel())
                .temperature(ragProperties.getOllama().getTemperature())
                .build();
    }

    /**
     * 构建Embedding向量模型Bean
     */
    @Bean
    public EmbeddingModel embeddingModel() {
        return OllamaEmbeddingModel.builder()
                .baseUrl(ragProperties.getOllama().getBaseUrl())
                .modelName(ragProperties.getOllama().getEmbeddingModel())
                .build();
    }

    /**
     * 构建Rerank评分模型Bean
     * 使用本地Ollama部署的BGE-Reranker模型进行文档重排序
     */
    @Bean
    public ScoringModel scoringModel() {
        return new OllamaScoringModel(
                ragProperties.getOllama().getBaseUrl(),
                ragProperties.getOllama().getRerankModel()
        );
    }


    /**
     * Chroma向量库Bean（文档知识库）
     */
    @Bean
    public EmbeddingStore<dev.langchain4j.data.segment.TextSegment> embeddingStore() {
        return ChromaEmbeddingStore.builder()
                .baseUrl(ragProperties.getChroma().getBaseUrl())
                .collectionName(ragProperties.getChroma().getCollectionName())
                .build();
    }

    /**
     * 图片向量库Bean（图片资源向量化存储）
     */
    @Bean
    public EmbeddingStore<dev.langchain4j.data.segment.TextSegment> imageEmbeddingStore() {
        return ChromaEmbeddingStore.builder()
                .baseUrl(ragProperties.getChroma().getBaseUrl())
                .collectionName("image_embeddings")
                .build();
    }

    /**
     * 音频向量库Bean（音频资源向量化存储）
     */
    @Bean
    public EmbeddingStore<dev.langchain4j.data.segment.TextSegment> audioEmbeddingStore() {
        return ChromaEmbeddingStore.builder()
                .baseUrl(ragProperties.getChroma().getBaseUrl())
                .collectionName("audio_embeddings")
                .build();
    }


    /**
     * 构建检索增强器（带Rerank重排）
     * 通过向量检索召回相关片段，再使用Rerank模型重排提升精度
     */
    @Bean
    public RetrievalAugmentor retrievalAugmentor(
            EmbeddingStore<dev.langchain4j.data.segment.TextSegment> embeddingStore,
            EmbeddingModel embeddingModel,
            ScoringModel scoringModel
    ) {
        // 向量检索：从向量库召回topK个候选片段
        var contentRetriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .maxResults(ragProperties.getRetrieve().getTopK())
                .build();

        // 配置Rerank重排聚合器
        ContentAggregator aggregator = ReRankingContentAggregator.builder()
                .scoringModel(scoringModel)                    // 设置评分模型
                .minScore(ragProperties.getRetrieve().getMinScore())  // 最低相关性阈值
                .build();

        // 构建带Rerank重排的检索增强器
        return DefaultRetrievalAugmentor.builder()
                .contentRetriever(contentRetriever)
                .contentAggregator(aggregator)  // 注入重排聚合器
                .build();
    }

    /**
     * Redis持久化多用户记忆提供器
     * 根据userId创建独立对话窗口，实现会话隔离
     */
    @Bean
    public ChatMemoryProvider chatMemoryProvider() {
        return userId -> MessageWindowChatMemory.builder()
                .maxMessages(MAX_HISTORY_MSG)
                .chatMemoryStore(redisChatMemoryStore)
                .build();
    }

    /**
     * 清空单个用户记忆
     */
    public void clearUserMemory(String userId) {
        redisChatMemoryStore.deleteMessages(userId);
    }

    /**
     * 清空全部用户记忆
     */
    public void clearAllMemory() {
        // Redis中所有rag:chat:memory:前缀的key都需要删除
        // 这里简化处理，实际生产环境建议使用scan批量删除
        redisChatMemoryStore.deleteMessages("*");
    }
}
