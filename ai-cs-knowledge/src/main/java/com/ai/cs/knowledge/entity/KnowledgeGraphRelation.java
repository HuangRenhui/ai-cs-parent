package com.ai.cs.knowledge.entity;

import com.ai.cs.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 知识图谱关系实体
 * 表示图谱中两个节点之间的关系
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("cs_knowledge_graph_relation")
public class KnowledgeGraphRelation extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 关系唯一标识
     */
    private String relationId;

    /**
     * 源节点ID
     */
    private String sourceNodeId;

    /**
     * 目标节点ID
     */
    private String targetNodeId;

    /**
     * 源节点名称
     */
    private String sourceNodeName;

    /**
     * 目标节点名称
     */
    private String targetNodeName;

    /**
     * 关系类型: 包含、依赖、属于、等同于、引用、相关、前置、后置
     */
    private String relationType;

    /**
     * 关系描述
     */
    private String description;

    /**
     * 关系属性（JSON格式存储额外信息）
     */
    private String properties;

    /**
     * 关系权重（0-100）
     */
    private Integer weight;

    /**
     * 关系置信度（0.0-1.0）
     */
    private Double confidence;

    /**
     * 来源文档ID
     */
    private String sourceDocumentId;

    /**
     * 状态: 0-草稿，1-已发布，2-已归档
     */
    private Integer status;

    @TableLogic
    private Integer delFlag;
}
