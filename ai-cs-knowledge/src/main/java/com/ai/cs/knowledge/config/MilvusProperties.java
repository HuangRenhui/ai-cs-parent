package com.ai.cs.knowledge.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "milvus")
public class MilvusProperties {
    private String host = "localhost";
    private Integer port = 19530;
    private String collectionName = "cs_kb_faq";
    private int nlist = 128;
    private int nprobe = 16;
    private int topK = 5;
    /** COSINE 分数下限，低于则视为未命中 */
    private float scoreThreshold = 0.40f;
    private int contentMaxLength = 8192;
    private int dimension = 1024;
}
