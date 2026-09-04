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
}
