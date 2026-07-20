package com.ai.cs.common.dto;

import lombok.Data;

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
}
