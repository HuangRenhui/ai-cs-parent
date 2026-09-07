package com.ai.cs.common.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class WidgetInitVO {
    private String tenantCode;
    private String channel;
    private String visitorRef;
    private String scene;
    private List<BizEntity> entities = new ArrayList<>();
    private Long customerId;
    /** C 端 Widget 短时 JWT，避免匿名聊窗被网关 401 */
    private String accessToken;
    /** 场景化开场白（按 scene 配置解析，{entityId} 已替换） */
    private String greeting;
    /** 场景快捷动作（首屏按钮，点击后以 send 文本走正常对话链路） */
    private List<SceneQuickAction> quickActions = new ArrayList<>();
}
