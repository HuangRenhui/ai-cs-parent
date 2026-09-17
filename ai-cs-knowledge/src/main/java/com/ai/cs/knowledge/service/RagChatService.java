package com.ai.cs.knowledge.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * RAG 聊天服务（占位）
 *
 * <p>TODO 后续实现：用 LangChain4j 的 {@code AiServices} 绑定
 * {@code ChatLanguageModel} + {@code RetrievalAugmentor} + {@code ChatMemoryProvider} 构建
 * {@code KnowledgeAssistant}，以 {@code @MemoryId} 实现用户级会话隔离；
 * {@link #chat} 即调用该助手的 {@code chat(userId, question)}（带记忆 + RAG 检索 + 自定义 Prompt）。</p>
 *
 * <p>当前不调用模型、也不接入对话记忆：{@link #chat} 与两个记忆清理方法均返回未实现提示。</p>
 */
@Slf4j
@Service
public class RagChatService {

    /** 占位统一文案前缀 */
    private static final String NOT_IMPLEMENTED = "为占位实现，后端未接入 LangChain4j 助手";

    /**
     * 根据用户ID对话，自动隔离历史（占位：不调用模型）
     *
     * @param userId   用户唯一id（账号id、设备id均可）
     * @param question 用户提问
     * @return 未实现提示
     */
    public String chat(String userId, String question) {
        log.warn("[占位] RAG 聊天未实现 userId={}", userId);
        return "RAG 聊天" + NOT_IMPLEMENTED;
    }

    /**
     * 清空单个用户对话记忆（占位：不写入/删除 Redis 记忆）
     *
     * @param userId 用户ID
     * @return 未实现提示
     */
    public String clearUserChatMemory(String userId) {
        log.warn("[占位] 清空用户对话记忆未实现 userId={}", userId);
        return "清空用户[" + userId + "]对话记忆" + NOT_IMPLEMENTED;
    }

    /**
     * 清空所有用户会话（占位：不写入/删除 Redis 记忆）
     *
     * @return 未实现提示
     */
    public String clearAllChatMemory() {
        log.warn("[占位] 清空全部对话记忆未实现");
        return "清空全部用户对话记忆" + NOT_IMPLEMENTED;
    }
}
