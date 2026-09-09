package com.ai.cs.knowledge.dto;

import lombok.Data;

/**
 * 知识库健康状态视图对象
 * 用于健康检查接口返回Milvus连通状态与当前模型信息
 */
@Data
public class KnowledgeHealthVO {
    /** Milvus连接状态描述信息 */
    private String milvus;
    /** 当前使用的向量集合名称 */
    private String collection;
    /** 知识库是否就绪（Milvus可用） */
    private boolean ready;
    /** 当前使用的Embedding模型名称 */
    private String embeddingModel;
}
