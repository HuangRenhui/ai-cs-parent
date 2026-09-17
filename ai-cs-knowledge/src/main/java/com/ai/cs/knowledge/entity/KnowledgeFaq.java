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
    /** 主键ID（自增） */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 租户编码，默认 default */
    private String tenantCode;
    /** 问题（FAQ标准问法） */
    private String question;
    /** 答案内容 */
    private String answer;
    /** 分类 */
    private String category;
    /** 排序号，越小越靠前 */
    private Integer sortNum;
    /** 状态：0-禁用，1-启用 */
    private Integer status;
    /** Milvus中的向量ID */
    private String milvusId;
    /** 审核状态：0-草稿，1-待审核，2-已发布，3-已下线 */
    private Integer auditStatus;
    /** 点赞数 */
    private Integer likeCount;
    /** 点踩数 */
    private Integer dislikeCount;
    /** 浏览数 */
    private Integer viewCount;
    /** 逻辑删除标记：0-正常，1-已删除 */
    @TableLogic
    private Integer delFlag;
}
