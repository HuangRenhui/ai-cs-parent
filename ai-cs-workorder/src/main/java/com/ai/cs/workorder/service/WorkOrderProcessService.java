package com.ai.cs.workorder.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Flowable 工单流程服务（占位）
 *
 * <p>承载 BPMN 流程中定义的 serviceTask 回调。当前三个回调**均为占位实现**：不分类、
 * 不回写工单状态（`order_type` / `order_status` 均不动），只记日志。</p>
 *
 * <p>TODO 后续实现：`autoClassify` 按关键词（退款→投诉→咨询→物流→建议）回写 `order_type`；
 * `autoReply` 把咨询类工单置为已完成（`order_status=3`）；
 * `completeWorkOrder` 在流程终点把工单置为已关闭（`order_status=4`）。</p>
 *
 * <p>本类仅在 `flowable.enabled=true` 时装配（默认 false），且当前无调用方。</p>
 *
 * @author huangrenhui
 * @date 2026/7/20
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "flowable.enabled", havingValue = "true", matchIfMissing = false)
public class WorkOrderProcessService {

    /**
     * 自动分类（占位：不分类、不更新工单类型）
     *
     * @param workOrderId 工单ID
     */
    public void autoClassify(Long workOrderId) {
        log.info("[占位] 工单自动分类未实现 workOrderId={}，不更新 order_type", workOrderId);
    }

    /**
     * AI自动回复（占位：不回写工单状态）
     *
     * @param workOrderId 工单ID
     */
    public void autoReply(Long workOrderId) {
        log.info("[占位] 工单 AI 自动回复未实现 workOrderId={}，不回写 order_status", workOrderId);
    }

    /**
     * 完成工单（占位：不回写工单状态）
     *
     * @param workOrderId 工单ID
     */
    public void completeWorkOrder(Long workOrderId) {
        log.info("[占位] 工单流程完成未实现 workOrderId={}，不回写 order_status", workOrderId);
    }
}
