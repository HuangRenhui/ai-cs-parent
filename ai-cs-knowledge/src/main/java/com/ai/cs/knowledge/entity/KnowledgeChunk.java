package com.ai.cs.knowledge.entity;

import com.ai.cs.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 文档切片实体（文档 RAG 投喂的落库载体）
 *
 * <p>切片文本落库后，主键 id 同时作为 Milvus 切片集合的主键 {@code chunk_id}，
 * 检索命中后可回表拿到切片全文、来源文档与序号；文档重切/删除时按 documentId 清理。</p>
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("cs_kb_chunk")
public class KnowledgeChunk extends BaseEntity {

    /** 主键ID（自增，同时作为 Milvus 中的 chunk_id） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 租户编码，默认 default */
    private String tenantCode;

    /** 来源文档ID（cs_document_version.document_id） */
    private String documentId;

    /** 来源文档名称 */
    private String documentName;

    /** 文档内切片序号，从 0 开始 */
    private Integer chunkIndex;

    /** 切片文本 */
    private String content;

    /** 是否已写入向量库：0-未向量化，1-已向量化 */
    private Integer vectorized;

    /** 逻辑删除标记：0-正常，1-已删除 */
    @TableLogic
    private Integer delFlag;
}
