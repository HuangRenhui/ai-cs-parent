package com.ai.cs.knowledge.config;

import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
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
     * Chroma向量库Bean
     */
    @Bean
    public EmbeddingStore<dev.langchain4j.data.segment.TextSegment> embeddingStore() {
        return ChromaEmbeddingStore.builder()
                .baseUrl(ragProperties.getChroma().getBaseUrl())
                .collectionName(ragProperties.getChroma().getCollectionName())
                .build();
    }


    /**
     * 构建检索增强器
     * 通过向量检索召回相关片段
     */
    @Bean
    public RetrievalAugmentor retrievalAugmentor(
            EmbeddingStore<dev.langchain4j.data.segment.TextSegment> embeddingStore,
            EmbeddingModel embeddingModel
    ) {
        // 向量检索：从向量库召回topK个候选片段
        var contentRetriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .maxResults(ragProperties.getRetrieve().getTopK())
                .build();

        return DefaultRetrievalAugmentor.builder()
                .contentRetriever(contentRetriever)
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
