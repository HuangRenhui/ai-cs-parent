package com.ai.cs.knowledge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cs_knowledge_miss")
public class KnowledgeMiss {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String tenantCode;
    private String question;
    private String sessionId;
    private Float topScore;
    private LocalDateTime createTime;
}
