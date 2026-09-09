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
    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 会话唯一标识（UUID） */
    private String sessionId;
    /** 客户ID */
    private Long customerId;
    /** 接待坐席ID（AI 会话时为空） */
    private Long agentId;
    /** 会话类型：1-AI会话 2-人工会话 */
    private Integer sessionType;
    /** 会话状态：1-进行中 2-已结束 */
    private Integer sessionStatus;
    /** 会话开始时间 */
    private LocalDateTime startTime;
    /** 会话结束时间 */
    private LocalDateTime endTime;
}
