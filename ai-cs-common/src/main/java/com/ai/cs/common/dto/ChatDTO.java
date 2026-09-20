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
    /**
     * 用户消息内容。
     * <p>允许为空：当用户只发送附件（图片/文档）而无文字时，msg 为空串由 {@code attachments} 承载内容，
     * 故此处不做 {@code @NotBlank} 强制校验，改由业务层判断「msg 与 attachments 至少有一项」。</p>
     */
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
    /** 行业提示词包编码（ecommerce/retail/finance 等），空则使用 PromptConst 通用人设 */
    private String packCode;
    /** 对话关联的业务实体（订单等），供场景话术与工具调用使用 */
    private List<BizEntity> entities;
    /**
     * 消息附件列表（图片 / 文档 / 音频等）。
     * <p>为空表示纯文本消息；当前阶段附件仅随消息落库与展示，
     * 解析结果回填到 {@link AttachmentDTO#getExtractedText()} 后即可参与大模型上下文拼装。</p>
     */
    private List<AttachmentDTO> attachments;
}
