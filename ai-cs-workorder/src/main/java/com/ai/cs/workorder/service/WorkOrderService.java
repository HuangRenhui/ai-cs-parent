package com.ai.cs.workorder.service;

/**
 *
 * @author huangrenhui
 * @date 2026/6/11 18:17
 * @description 生成工单编号 + 新建工单
 */

import cn.hutool.core.lang.UUID;
import com.ai.cs.common.dto.WorkOrderDTO;
import com.ai.cs.workorder.entity.WorkOrder;
import com.ai.cs.workorder.mapper.WorkOrderMapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class WorkOrderService extends ServiceImpl<WorkOrderMapper, WorkOrder> {

    public String createOrder(WorkOrderDTO dto) {
        WorkOrder order = new WorkOrder();
        // 唯一工单编号
        order.setOrderNo("WO_" + UUID.randomUUID().toString(true));
        order.setSessionId(dto.getSessionId());
        order.setCustomerId(dto.getCustomerId());
        order.setOrderType(dto.getOrderType());
        order.setOrderContent(dto.getContent());
        order.setOrderStatus(1);
        order.setAgentId(0L);
        this.save(order);
        return order.getOrderNo();
    }
}
