package com.ai.cs.knowledge.entity;

import com.ai.cs.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 多模态知识条目实体
 * 用于存储图片问答、音频问答、视频检索等非文本知识
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("cs_multimodal_knowledge")
public class MultimodalKnowledge extends BaseEntity {

    /** 主键ID（自增） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 知识条目唯一标识
     */
    private String knowledgeId;

    /**
     * 模态类型: IMAGE, AUDIO, VIDEO, MIXED
     */
    private String modality;

    /**
     * 资源文件路径
     */
    private String resourcePath;

    /**
     * 资源文件ID（关联media资源）
     */
    private String resourceId;

    /**
     * 知识标题
     */
    private String title;

    /**
     * 知识内容描述（文本摘要）
     */
    private String description;

    /**
     * AI生成的详细分析文本
     */
    private String analysis;

    /**
     * 提取的关键词（JSON数组）
     */
    private String keywords;

    /**
     * 提取的实体（JSON数组）
     */
    private String entities;

    /**
     * 标签列表（逗号分隔）
     */
    private String tags;

    /**
     * 来源文档ID
     */
    private String sourceDocumentId;

    /**
     * 向量ID（Chroma/Milvus中的ID）
     */
    private String vectorId;

    /**
     * 向量集合名称
     */
    private String vectorCollection;

    /**
     * 置信度（0.0-1.0）
     */
    private Double confidence;

    /**
     * 状态: 0-处理中，1-已入库，2-已归档
     */
    private Integer status;

    /** 逻辑删除标记：0-正常，1-已删除 */
    @TableLogic
    private Integer delFlag;
}
