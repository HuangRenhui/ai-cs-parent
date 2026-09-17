package com.ai.cs.ops.dto;

import lombok.Data;

/**
 * 告警事件视图对象：一条处于触发中（FIRING）的告警。
 */
@Data
public class OpsAlertEventVO {
    /** 触发该事件的规则编码 */
    private String ruleCode;
    /** 告警标题 */
    private String title;
    /** 事件状态（FIRING 触发中） */
    private String status;
    /** 告警详情描述 */
    private String message;
    /** 触发时间（yyyy-MM-dd HH:mm:ss） */
    private String time;
}
