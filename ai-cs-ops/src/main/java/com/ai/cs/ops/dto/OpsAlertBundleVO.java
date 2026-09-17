package com.ai.cs.ops.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 告警快照视图对象：规则列表 + 当前触发中的事件 + 提示文案，一次接口全量返回。
 */
@Data
public class OpsAlertBundleVO {
    /** 全部告警规则 */
    private List<OpsAlertRuleVO> rules = new ArrayList<>();
    /** 当前触发中（FIRING）的告警事件 */
    private List<OpsAlertEventVO> events = new ArrayList<>();
    /** 页面提示文案（说明当前实现阶段与局限） */
    private String hint;
}
