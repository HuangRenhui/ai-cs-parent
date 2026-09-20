package com.ai.cs.base.entity;

/**
 *
 * @author huangrenhui
 * @date 2026/6/11 18:13
 * @description 消息实体
 */

import com.ai.cs.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@TableName(value = "cs_chat_msg", autoResultMap = true)
public class ChatMsg extends BaseEntity {
    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 所属会话ID */
    private String sessionId;
    /** 消息内容（纯附件消息可为空串） */
    private String msgContent;
    /** 消息类型：1-用户消息 2-AI消息 3-人工消息 */
    private Integer msgType;
    /**
     * 消息附件列表（图片/文档等）。
     * <p>以 JSON 存库，空表示纯文本消息。用 Map 接收而非 AttachmentDTO，
     * 避免 base-service 与 common 模块间的循环依赖，同时保留全部字段便于前端渲染。</p>
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Map<String, Object>> attachments;
}
