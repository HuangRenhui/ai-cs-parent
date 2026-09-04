package com.ai.cs.knowledge.dto;

import lombok.Data;

@Data
public class KnowledgeHealthVO {
    private String milvus;
    private String collection;
    private boolean ready;
    private String embeddingModel;
}
