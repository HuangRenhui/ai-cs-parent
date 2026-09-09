package com.ai.cs.ops.dto;

import lombok.Data;

/**
 * 告警规则视图对象：规则定义及启停、通知通道配置（当前仅存内存）。
 */
@Data
public class OpsAlertRuleVO {
    /** 规则编码（唯一标识，如 service.down） */
    private String code;
    /** 规则名称 */
    private String name;
    /** 规则说明 */
    private String description;
    /** 1 启用 0 停用 */
    private Integer enabled;
    /** 通知通道（none/邮件/企微等，推送通道属后续阶段接入） */
    private String channel;
}
