package com.ai.cs.common.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Widget 初始化请求：C 端聊窗加载时上报上下文，换取初始化配置（见 {@link WidgetInitVO}）
 *
 * @author ai-cs
 */
@Data
public class WidgetInitDTO {
    /** 租户编码，默认 default */
    private String tenantCode;
    /** 接入渠道（web/app/h5 等） */
    private String channel;
    /** 访客标识（前端生成的匿名指纹） */
    private String visitorRef;
    /** 业务场景编码（决定开场白与快捷动作） */
    private String scene;
    /** 当前页面携带的业务实体（如正在查看的订单） */
    private List<BizEntity> entities = new ArrayList<>();
    /** 语言地区（预留国际化） */
    private String locale;
    /** 客户ID（已登录用户） */
    private Long customerId;
}
