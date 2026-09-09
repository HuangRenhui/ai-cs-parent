package com.ai.cs.knowledge.util;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Milvus检索命中结果
 * 封装一次向量检索命中的FAQ记录及其相似度分数
 */
@Data
@AllArgsConstructor
public class MilvusHit {
    /** 命中的FAQ主键ID */
    private long faqId;
    /** 相似度分数（COSINE距离，越大越相似） */
    private float score;
    /** 命中的FAQ内容（问题+答案拼接文本） */
    private String content;
}
