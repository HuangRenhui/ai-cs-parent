package com.ai.cs.base.entity;

/**
 *
 * @author huangrenhui
 * @date 2026/6/11 18:13
 * @description 消息实体
 */

import com.ai.cs.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("cs_chat_msg")
public class ChatMsg extends BaseEntity {
    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 所属会话ID */
    private String sessionId;
    /** 消息内容 */
    private String msgContent;
    /** 消息类型：1-用户消息 2-AI消息 3-人工消息 */
    private Integer msgType;
}
