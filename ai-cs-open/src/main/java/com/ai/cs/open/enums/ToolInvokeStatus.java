package com.ai.cs.open.enums;

/**
 * 工具调用/人工确认状态机。
 *
 * <pre>
 * (发起) ─▶ CONFIRMING ──同意──▶ CONFIRMED ──▶ SUCCEEDED / FAILED
 *             │  │                 ↑ 幂等键在此刻写入
 *             │  └──坐席拒绝──▶ REJECTED
 *             ├──超时(expire_at)──▶ EXPIRED
 *             └──取消──▶ CANCELLED
 * </pre>
 *
 * <p>read 级工具与显式 confirmed=true 的调用不经过 CONFIRMING，直接记为 CONFIRMED。</p>
 */
public enum ToolInvokeStatus {

    /** 已落待确认记录，等待有权限的人确认 */
    CONFIRMING,
    /** 已确认（幂等键已写入），正在执行 */
    CONFIRMED,
    /** 执行成功 */
    SUCCEEDED,
    /** 执行失败 */
    FAILED,
    /** 坐席拒绝 */
    REJECTED,
    /** 超时失效 */
    EXPIRED,
    /** 发起方取消 */
    CANCELLED;

    /**
     * 是否终态。终态不可再流转，重复操作只能重放或提示已处理。
     */
    public boolean isTerminal() {
        return this == SUCCEEDED || this == FAILED || this == REJECTED
                || this == EXPIRED || this == CANCELLED;
    }
}
