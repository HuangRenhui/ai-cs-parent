package com.ai.cs.workorder.service;

import cn.hutool.core.lang.UUID;
import com.ai.cs.common.dto.WorkOrderDTO;
import com.ai.cs.common.exception.BusinessException;
import com.ai.cs.common.util.ValidateUtil;
import com.ai.cs.workorder.entity.WorkOrder;
import com.ai.cs.workorder.mapper.WorkOrderMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 工单服务（完整CRUD + 智能处理）
 *
 * @author huangrenhui
 * @date 2026/6/11 18:17
 */
@Slf4j
@Service
public class WorkOrderService extends ServiceImpl<WorkOrderMapper, WorkOrder> {

    /**
     * 创建工单
     */
    public String createOrder(WorkOrderDTO dto) {
        if (dto == null) {
            throw new BusinessException("工单内容不能为空");
        }
        ValidateUtil.requireOrderType(dto.getOrderType());
        ValidateUtil.requireLength(dto.getContent(), "工单内容", 1, 2000);
        ValidateUtil.optionalSessionId(dto.getSessionId());
        WorkOrder order = new WorkOrder();
        order.setOrderNo("WO_" + UUID.randomUUID().toString(true));
        order.setSessionId(dto.getSessionId());
        order.setCustomerId(dto.getCustomerId() == null ? 0L : dto.getCustomerId());
        order.setOrderType(dto.getOrderType().trim());
        order.setOrderContent(dto.getContent().trim());
        order.setOrderStatus(1);
        order.setAgentId(0L);
        this.save(order);
        log.info("工单创建成功: {}", order.getOrderNo());
        return order.getOrderNo();
    }

    public Page<WorkOrder> queryPage(int pageNum, int pageSize, String orderType,
                                      Integer orderStatus, Long agentId, String keyword) {
        pageNum = ValidateUtil.pageNum(pageNum);
        pageSize = ValidateUtil.pageSize(pageSize);
        LambdaQueryWrapper<WorkOrder> wrapper = new LambdaQueryWrapper<>();
        if (orderType != null && !orderType.isBlank()) {
            wrapper.eq(WorkOrder::getOrderType, orderType);
        }
        if (orderStatus != null) {
            wrapper.eq(WorkOrder::getOrderStatus, orderStatus);
        }
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like(WorkOrder::getOrderNo, keyword)
                    .or().like(WorkOrder::getOrderContent, keyword));
        }
        if (agentId != null) {
            wrapper.eq(WorkOrder::getAgentId, agentId);
        }
        wrapper.orderByDesc(WorkOrder::getCreateTime);
        return this.page(new Page<>(pageNum, pageSize), wrapper);
    }

    /**
     * 分配工单给坐席
     */
    public void assignOrder(Long orderId, Long agentId) {
        WorkOrder order = this.getById(orderId);
        if (order == null) {
            throw new BusinessException("工单不存在");
        }
        order.setAgentId(agentId);
        order.setOrderStatus(2); // 处理中
        this.updateById(order);
        log.info("工单 {} 已分配给坐席 {}", order.getOrderNo(), agentId);
    }

    /**
     * 更新工单状态
     */
    public void updateStatus(Long orderId, Integer status) {
        WorkOrder order = this.getById(orderId);
        if (order == null) {
            throw new BusinessException("工单不存在");
        }
        order.setOrderStatus(status);
        this.updateById(order);
        log.info("工单 {} 状态更新为 {}", order.getOrderNo(), status);
    }

    /**
     * 查找相似工单（基于关键词匹配）
     */
    public List<WorkOrder> findSimilarOrders(Long orderId) {
        WorkOrder order = this.getById(orderId);
        if (order == null || order.getOrderContent() == null) {
            return new ArrayList<>();
        }

        // 提取关键词（简单实现：取前20个字符）
        String keyword = order.getOrderContent().length() > 20
                ? order.getOrderContent().substring(0, 20) : order.getOrderContent();

        return this.list(new LambdaQueryWrapper<WorkOrder>()
                .like(WorkOrder::getOrderContent, keyword)
                .ne(WorkOrder::getId, orderId)
                .last("LIMIT 5"));
    }

    /**
     * 工单智能分类（基于关键词规则）
     */
    public String autoClassify(Long orderId) {
        WorkOrder order = this.getById(orderId);
        if (order == null || order.getOrderContent() == null) {
            return "其他";
        }

        String content = order.getOrderContent().toLowerCase();

        if (content.contains("退款") || content.contains("退货") || content.contains("refund")) {
            order.setOrderType("退款");
            this.updateById(order);
            return "退款";
        } else if (content.contains("投诉") || content.contains("抱怨") || content.contains("complaint")) {
            order.setOrderType("投诉");
            this.updateById(order);
            return "投诉";
        } else if (content.contains("咨询") || content.contains("帮助") || content.contains("help")) {
            order.setOrderType("咨询");
            this.updateById(order);
            return "咨询";
        } else if (content.contains("建议") || content.contains("反馈") || content.contains("feedback")) {
            order.setOrderType("建议");
            this.updateById(order);
            return "建议";
        } else if (content.contains("物流") || content.contains("快递") || content.contains("delivery")) {
            order.setOrderType("物流");
            this.updateById(order);
            return "物流";
        }

        return order.getOrderType() != null ? order.getOrderType() : "其他";
    }
}
