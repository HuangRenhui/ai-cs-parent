package com.ai.cs.common.dto;

import lombok.Data;

import java.util.List;

/**
 * 聊天 DTO
 *
 * @author huangrenhui
 * @date 2026/6/11 17:45
 */
@Data
public class ChatDTO {
    /** 会话ID，空则由服务端创建新会话 */
    private String sessionId;
    /** 用户消息内容 */
    private String msg;
    /** 历史对话文本（调用方自带时优先于服务端上下文） */
    private String history;
    /** 客户ID（已登录用户） */
    private Long customerId;
    /** 访客标识（未登录 C 端用户的前端指纹） */
    private String visitorRef;
    /** 业务场景编码（如 order/after-sale），决定开场白与快捷动作 */
    private String scene;
    /** 接入渠道（web/app/h5 等） */
    private String channel;
    /** 知识库租户，默认 default */
    private String tenantCode;
    /** 对话关联的业务实体（订单等），供场景话术与工具调用使用 */
    private List<BizEntity> entities;
}
