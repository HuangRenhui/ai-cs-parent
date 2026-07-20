package com.ai.cs.knowledge.entity;

import com.ai.cs.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * FAQ知识库实体
 *
 * @author huangrenhui
 * @date 2026/6/11 18:07
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("cs_knowledge_faq")
public class KnowledgeFaq extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String question;
    private String answer;
    private String category;
    private Integer sortNum;
    private Integer status;
    private String milvusId; // Milvus中的向量ID
    @TableLogic
    private Integer delFlag;
}
