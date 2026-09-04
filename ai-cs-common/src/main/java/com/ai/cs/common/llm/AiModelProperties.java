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

    private Llm llm = new Llm();
    private Embedding embedding = new Embedding();

    @Data
    public static class Llm {
        private String apiKey;
        private String apiSecret;
        private String model = "qwen-turbo";
        private String url = "https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation";
        private int timeoutSeconds = 30;
    }

    @Data
    public static class Embedding {
        /** 为空则回退 {@link Llm#apiKey}。 */
        private String apiKey;
        private String model = "text-embedding-v3";
        private String url = "https://dashscope.aliyuncs.com/api/v1/services/embeddings/text-embedding/text-embedding";
        private int dimension = 1024;
        private int timeoutSeconds = 15;
    }
}
