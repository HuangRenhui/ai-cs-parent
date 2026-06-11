package com.ai.cs.base.entity;

/**
 *
 * @author huangrenhui
 * @date 2026/6/11 18:11
 * @description 会话实体
 */

import com.ai.cs.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("cs_chat_session")
public class ChatSession extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String sessionId;
    private Long customerId;
    private Long agentId;
    private Integer sessionType;
    private Integer sessionStatus;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
}
