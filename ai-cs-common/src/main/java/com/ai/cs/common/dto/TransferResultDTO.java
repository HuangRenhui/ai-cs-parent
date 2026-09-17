package com.ai.cs.common.dto;

import lombok.Data;

/**
 * 转人工结果：把接入坐席的工号、姓名带回给聊天页展示，避免只提示「已转接」。
 */
@Data
public class TransferResultDTO {
    /** 坐席主键，无在线坐席时为空 */
    private Long agentId;
    /** 对外工号，如 A001 */
    private String agentNo;
    /** 坐席登录账号 */
    private String agentAccount;
    /** 坐席姓名 */
    private String agentName;
    /** 技能组（无配置时为综合客服） */
    private String skill;
    /** 排队等待秒数；已接入为 0 */
    private Integer waitSeconds;

    /**
     * 按坐席主键生成工号：A + 三位数字，例如 id=2 → A002
     */
    public static String toAgentNo(Long agentId) {
        if (agentId == null) {
            return "";
        }
        return "A" + String.format("%03d", agentId);
    }
}
