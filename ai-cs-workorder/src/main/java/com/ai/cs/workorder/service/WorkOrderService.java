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
 * 工单服务（完整 CRUD；智能处理为占位实现）
 *
 * @author huangrenhui
 * @date 2026/6/11 18:17
 */
@Slf4j
@Service
public class WorkOrderService extends ServiceImpl<WorkOrderMapper, WorkOrder> {

    /**
     * 创建工单
     *
     * @param dto 工单入参（类型、内容、可选的会话ID/客户ID）
     * @return 生成的工单编号（WO_ 前缀）
     */
    public String createOrder(WorkOrderDTO dto) {
        if (dto == null) {
            throw new BusinessException("工单内容不能为空");
        }
        // 入参校验：类型合法性、内容长度、会话ID格式
        ValidateUtil.requireOrderType(dto.getOrderType());
        ValidateUtil.requireLength(dto.getContent(), "工单内容", 1, 2000);
        ValidateUtil.optionalSessionId(dto.getSessionId());
        WorkOrder order = new WorkOrder();
        // 工单号用去横线 UUID 保证全局唯一
        order.setOrderNo("WO_" + UUID.randomUUID().toString(true));
        order.setSessionId(dto.getSessionId());
        order.setCustomerId(dto.getCustomerId() == null ? 0L : dto.getCustomerId());
        order.setOrderType(dto.getOrderType().trim());
        order.setOrderContent(dto.getContent().trim());
        // 新工单初始状态为 1-待处理，坐席置 0 表示未分配
        order.setOrderStatus(1);
        order.setAgentId(0L);
        this.save(order);
        log.info("工单创建成功: {}", order.getOrderNo());
        return order.getOrderNo();
    }

    /**
     * 分页条件查询工单列表
     *
     * @param pageNum     页码（非法值由 ValidateUtil 矫正）
     * @param pageSize    每页条数
     * @param orderType   工单类型筛选，可空
     * @param orderStatus 工单状态筛选，可空
     * @param agentId     坐席ID筛选，可空
     * @param keyword     关键字，模糊匹配工单号或工单内容，可空
     * @return 分页结果，按创建时间倒序
     */
    public Page<WorkOrder> queryPage(int pageNum, int pageSize, String orderType,
                                      Integer orderStatus, Long agentId, String keyword) {
        pageNum = ValidateUtil.pageNum(pageNum);
        pageSize = ValidateUtil.pageSize(pageSize);
        // 动态拼装查询条件：仅拼接非空条件
        LambdaQueryWrapper<WorkOrder> wrapper = new LambdaQueryWrapper<>();
        if (orderType != null && !orderType.isBlank()) {
            wrapper.eq(WorkOrder::getOrderType, orderType);
        }
        if (orderStatus != null) {
            wrapper.eq(WorkOrder::getOrderStatus, orderStatus);
        }
        if (keyword != null && !keyword.isBlank()) {
            // 关键字同时匹配工单号和工单内容，两个条件之间是 OR 关系
            wrapper.and(w -> w.like(WorkOrder::getOrderNo, keyword)
                    .or().like(WorkOrder::getOrderContent, keyword));
        }
        if (agentId != null) {
            wrapper.eq(WorkOrder::getAgentId, agentId);
        }
        // 最新创建的工单排前面
        wrapper.orderByDesc(WorkOrder::getCreateTime);
        return this.page(new Page<>(pageNum, pageSize), wrapper);
    }

    /**
     * 分配工单给坐席
     *
     * @param orderId 工单ID
     * @param agentId 坐席ID；分配后状态由 1-待处理 流转为 2-处理中
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
     *
     * @param orderId 工单ID
     * @param status  目标状态：0-已取消 1-待处理 2-处理中 3-已完成 4-已关闭（不校验流转合法性，由调用方保证）
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
     * 查找相似工单（占位：恒返回空列表）。
     *
     * <p>TODO 后续实现：按当前工单内容提取关键词，检索内容相似的其它工单（最多 5 条，排除自身）。
     * 当前「智能推荐相似工单」接口恒无结果。</p>
     *
     * @param orderId 当前工单ID
     * @return 空列表
     */
    public List<WorkOrder> findSimilarOrders(Long orderId) {
        log.info("[占位] 相似工单检索未实现 orderId={}，返回空列表", orderId);
        return new ArrayList<>();
    }

    /**
     * 工单智能分类（占位：恒返回 null，且不更新工单类型）。
     *
     * <p>TODO 后续实现：按关键词规则（优先级 退款→投诉→咨询→建议→物流）判定类型并回写 {@code order_type}；
     * 未命中任何规则时保留原有类型、无类型则归为「其他」。
     * 当前既不分类也不写库，返回值固定为 null。</p>
     *
     * @param orderId 工单ID
     * @return null（占位）
     */
    public String autoClassify(Long orderId) {
        log.info("[占位] 工单智能分类未实现 orderId={}，返回 null", orderId);
        return null;
    }
}
