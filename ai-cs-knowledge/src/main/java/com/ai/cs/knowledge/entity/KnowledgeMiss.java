package com.ai.cs.knowledge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识未命中记录实体
 * 记录用户提问但知识库未命中（相似度低于阈值）的问题，用于运营回收补充知识
 */
@Data
@TableName("cs_knowledge_miss")
public class KnowledgeMiss {
    /** 主键ID（自增） */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 租户编码 */
    private String tenantCode;
    /** 用户提问内容 */
    private String question;
    /** 会话ID，用于追溯提问上下文 */
    private String sessionId;
    /** 本次检索的最高相似度分数（低于阈值才记录） */
    private Float topScore;
    /** 记录创建时间 */
    private LocalDateTime createTime;
}
