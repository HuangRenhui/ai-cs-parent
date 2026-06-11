package com.ai.cs.base.entity;

/**
 *
 * @author huangrenhui
 * @date 2026/6/11 18:13
 * @description 消息实体
 */

import com.ai.cs.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("cs_chat_msg")
public class ChatMsg extends BaseEntity {
    private Long id;
    private String sessionId;
    private String msgContent;
    private Integer msgType;
}
