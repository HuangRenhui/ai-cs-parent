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
    private String sessionId;
    private String msg;
    private String history;
    private Long customerId;
    private String visitorRef;
    private String scene;
    private String channel;
    /** 知识库租户，默认 default */
    private String tenantCode;
    private List<BizEntity> entities;
}
