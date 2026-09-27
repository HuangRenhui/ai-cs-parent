package com.ai.cs.knowledge.util;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Milvus 文档切片检索命中结果
 * 封装一次切片向量检索命中的切片及其来源文档信息
 */
@Data
@AllArgsConstructor
public class MilvusChunkHit {
    /** 命中的切片主键（cs_kb_chunk.id） */
    private long chunkId;
    /** 来源文档ID（cs_document_version.document_id） */
    private String documentId;
    /** 文档内切片序号 */
    private int chunkIndex;
    /** 切片文本 */
    private String content;
    /** 相似度分数（COSINE，越大越相似） */
    private float score;
}
