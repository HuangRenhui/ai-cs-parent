package com.ai.cs.knowledge.entity;

import com.ai.cs.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 知识图谱节点实体
 * 表示知识图谱中的一个实体节点
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("cs_knowledge_graph_node")
public class KnowledgeGraphNode extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 节点唯一标识
     */
    private String nodeId;

    /**
     * 节点名称（实体名称）
     */
    private String name;

    /**
     * 节点标签/类型: 概念、人物、文档、技术、产品等
     */
    private String label;

    /**
     * 节点属性（JSON格式存储额外信息）
     */
    private String properties;

    /**
     * 节点描述
     */
    private String description;

    /**
     * 来源文档ID
     */
    private String sourceDocumentId;

    /**
     * 来源文档名称
     */
    private String sourceDocumentName;

    /**
     * 重要性评分（0-100）
     */
    private Integer importance;

    /**
     * 是否为核心节点
     */
    private Integer isCore;

    /**
     * 状态: 0-草稿，1-已发布，2-已归档
     */
    private Integer status;

    @TableLogic
    private Integer delFlag;
}
