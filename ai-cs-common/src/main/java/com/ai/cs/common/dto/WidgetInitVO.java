package com.ai.cs.common.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Widget 初始化响应：回显上下文 + 访客令牌 + 场景化首屏内容
 *
 * @author ai-cs
 */
@Data
public class WidgetInitVO {
    /** 租户编码（回显） */
    private String tenantCode;
    /** 接入渠道（回显） */
    private String channel;
    /** 访客标识（回显） */
    private String visitorRef;
    /** 业务场景编码（回显） */
    private String scene;
    /** 业务实体（回显） */
    private List<BizEntity> entities = new ArrayList<>();
    /** 客户ID（回显） */
    private Long customerId;
    /** C 端 Widget 短时 JWT，避免匿名聊窗被网关 401 */
    private String accessToken;
    /** 场景化开场白（按 scene 配置解析，{entityId} 已替换） */
    private String greeting;
    /** 场景快捷动作（首屏按钮，点击后以 send 文本走正常对话链路） */
    private List<SceneQuickAction> quickActions = new ArrayList<>();
}
