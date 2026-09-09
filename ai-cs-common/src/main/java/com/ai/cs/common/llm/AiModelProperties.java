package com.ai.cs.common.llm;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 对话与向量共用一套供应商配置。管理页尚未落地前，各服务读同一前缀 {@code ai.*}。
 */
@Data
@Component
@ConfigurationProperties(prefix = "ai")
public class AiModelProperties {

    /** 对话模型配置（ai.llm.*） */
    private Llm llm = new Llm();
    /** 向量模型配置（ai.embedding.*） */
    private Embedding embedding = new Embedding();

    @Data
    public static class Llm {
        /** DashScope API Key */
        private String apiKey;
        /** DashScope API Secret（原生协议 Basic 认证用） */
        private String apiSecret;
        /** 对话模型标识 */
        private String model = "qwen-turbo";
        /** 原生 generation 接口地址 */
        private String url = "https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation";
        /** 读超时(秒)，下限 5 秒 */
        private int timeoutSeconds = 30;
    }

    @Data
    public static class Embedding {
        /** 为空则回退 {@link Llm#apiKey}。 */
        private String apiKey;
        /** 向量模型标识 */
        private String model = "text-embedding-v3";
        /** 原生 text-embedding 接口地址 */
        private String url = "https://dashscope.aliyuncs.com/api/v1/services/embeddings/text-embedding/text-embedding";
        /** 向量维度（须与模型实际输出一致，影响知识库存储） */
        private int dimension = 1024;
        /** 读超时(秒)，下限 5 秒 */
        private int timeoutSeconds = 15;
    }
}
