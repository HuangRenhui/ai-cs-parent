package com.ai.cs.knowledge.service;

import com.ai.cs.knowledge.config.LangChainConfig;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import org.springframework.stereotype.Service;

/**
 * RAG聊天服务
 * 提供基于知识库的智能问答功能，支持多用户会话隔离
 */
@Service
public class RagChatService {

    /**
     * AI助手接口定义
     * 使用@MemoryId实现用户级别的会话隔离
     */
    public interface KnowledgeAssistant {
        String chat(@MemoryId String userId, @UserMessage String question);
    }

    private final KnowledgeAssistant assistant;
    private final LangChainConfig langChainConfig;

    /**
     * 构造注入全部依赖，自动绑定记忆+RAG检索+自定义Prompt
     * @param chatModel 大语言模型
     * @param retrievalAugmentor 检索增强器
     * @param chatMemoryProvider 对话记忆提供器
     * @param langChainConfig LangChain配置类
     */
    public RagChatService(ChatLanguageModel chatModel,
                           RetrievalAugmentor retrievalAugmentor,
                           ChatMemoryProvider chatMemoryProvider,
                           LangChainConfig langChainConfig) {
        this.assistant = AiServices.builder(KnowledgeAssistant.class)
                .chatLanguageModel(chatModel)
                .retrievalAugmentor(retrievalAugmentor)
                .chatMemoryProvider(chatMemoryProvider)
                .build();
        this.langChainConfig = langChainConfig;
    }

    /**
     * 根据用户ID对话，自动隔离历史
     * @param userId 用户唯一id（账号id、设备id均可）
     * @param question 用户提问
     * @return AI回答
     */
    public String chat(String userId, String question) {
        return assistant.chat(userId, question);
    }

    /**
     * 清空单个用户对话记忆
     * @param userId 用户ID
     * @return 操作结果信息
     */
    public String clearUserChatMemory(String userId) {
        langChainConfig.clearUserMemory(userId);
        return "用户[" + userId + "]对话记忆已清空";
    }

    /**
     * 清空所有用户会话
     * @return 操作结果信息
     */
    public String clearAllChatMemory() {
        langChainConfig.clearAllMemory();
        return "全部用户对话记忆已清空";
    }
}
