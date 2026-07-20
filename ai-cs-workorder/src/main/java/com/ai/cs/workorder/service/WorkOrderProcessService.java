package com.ai.cs.workorder.service;

import com.ai.cs.workorder.entity.WorkOrder;
import com.ai.cs.workorder.mapper.WorkOrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;

/**
 * Flowable 工单流程服务
 * 实现 BPMN 流程中定义的 serviceTask 和流程操作
 *
 * @author huangrenhui
 * @date 2026/7/20
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "flowable.enabled", havingValue = "true", matchIfMissing = false)
public class WorkOrderProcessService {

    @Resource
    private WorkOrderMapper workOrderMapper;

    /**
     * 自动分类（由 Flowable serviceTask 调用）
     */
    public void autoClassify(Long workOrderId) {
        log.info("[Flowable] 工单 {} 自动分类", workOrderId);

        WorkOrder order = workOrderMapper.selectById(workOrderId);
        if (order == null || order.getOrderContent() == null) {
            return;
        }

        String content = order.getOrderContent().toLowerCase();

        if (content.contains("退款") || content.contains("退货") || content.contains("refund")) {
            order.setOrderType("退款");
        } else if (content.contains("投诉") || content.contains("抱怨") || content.contains("complaint")) {
            order.setOrderType("投诉");
        } else if (content.contains("咨询") || content.contains("帮助") || content.contains("help")) {
            order.setOrderType("咨询");
        } else if (content.contains("物流") || content.contains("快递") || content.contains("delivery")) {
            order.setOrderType("物流");
        } else if (content.contains("建议") || content.contains("反馈") || content.contains("feedback")) {
            order.setOrderType("建议");
        }

        workOrderMapper.updateById(order);
    }

    /**
     * AI自动回复（咨询类工单）
     */
    public void autoReply(Long workOrderId) {
        log.info("[Flowable] 工单 {} AI自动回复", workOrderId);

        WorkOrder order = workOrderMapper.selectById(workOrderId);
        if (order == null) return;

        // 咨询类工单自动标记为已完成（已由AI处理）
        order.setOrderStatus(3); // 已完成
        workOrderMapper.updateById(order);
    }

    /**
     * 完成工单
     */
    public void completeWorkOrder(Long workOrderId) {
        log.info("[Flowable] 工单 {} 流程完成", workOrderId);

        WorkOrder order = workOrderMapper.selectById(workOrderId);
        if (order == null) return;

        order.setOrderStatus(4); // 已关闭
        workOrderMapper.updateById(order);
    }
}
