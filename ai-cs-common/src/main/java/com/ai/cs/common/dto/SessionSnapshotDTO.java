package com.ai.cs.common.dto;

import lombok.Data;

/**
 * 会话快照：跨服务查询会话归属（WebSocket 握手鉴权、转人工分配）时使用，避免 Feign 依赖实体类。
 */
@Data
public class SessionSnapshotDTO {
    /** 会话唯一标识 */
    private String sessionId;
    /** 客户 ID（未登录访客为 0） */
    private Long customerId;
    /** 外部访客标识，WebSocket 握手时校验访客只能连自己的会话 */
    private String visitorRef;
    /** 接待坐席 ID */
    private Long agentId;
    /** 会话类型：1-AI 2-人工 */
    private Integer sessionType;
    /** 会话状态：1-进行中 2-已结束 */
    private Integer sessionStatus;
}
