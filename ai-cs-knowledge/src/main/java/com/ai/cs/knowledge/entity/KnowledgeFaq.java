package com.ai.cs.knowledge.entity;

/**
 *
 * @author huangrenhui
 * @date 2026/6/11 18:07
 * @description TODO
 */

import com.ai.cs.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@Data
@TableName("cs_knowledge_faq")
public class KnowledgeFaq extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String question;
    private String answer;
    private String category;
    private Integer sortNum;
    private Integer status;
    @TableLogic
    private Integer delFlag;
}
