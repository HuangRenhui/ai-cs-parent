package com.ai.cs.workorder.service;

import com.ai.cs.common.dto.WorkOrderDTO;
import com.ai.cs.workorder.entity.WorkOrder;
import com.ai.cs.workorder.mapper.WorkOrderMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * WorkOrderService 单元测试
 *
 * @author huangrenhui
 * @date 2026/7/20
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("WorkOrderService 单元测试")
class WorkOrderServiceTest {

    @Mock
    private WorkOrderMapper workOrderMapper;

    private WorkOrderService workOrderService;

    private WorkOrder testOrder;
    private WorkOrderDTO testDTO;

    @BeforeEach
    void setUp() {
        workOrderService = new WorkOrderService();
        ReflectionTestUtils.setField(workOrderService, "baseMapper", workOrderMapper);

        testOrder = new WorkOrder();
        testOrder.setId(1L);
        testOrder.setOrderNo("WO_test001");
        testOrder.setOrderContent("测试工单内容");
        testOrder.setOrderType("咨询");
        testOrder.setOrderStatus(1);
        testOrder.setCustomerId(100L);
        testOrder.setAgentId(0L);
        testOrder.setSessionId("session-001");

        testDTO = new WorkOrderDTO();
        testDTO.setSessionId("session-001");
        testDTO.setCustomerId(100L);
        testDTO.setOrderType("投诉");
        testDTO.setContent("客服态度差");
    }

    @Test
    @DisplayName("创建工单 - 正常场景")
    void testCreateOrder() {
        when(workOrderMapper.insert(any(WorkOrder.class))).thenReturn(1);

        String orderNo = workOrderService.createOrder(testDTO);
        assertNotNull(orderNo);
        assertTrue(orderNo.startsWith("WO_"));
    }

    @Test
    @DisplayName("分配工单 - 工单不存在抛异常")
    void testAssignOrder_NotFound() {
        when(workOrderMapper.selectById(1L)).thenReturn(null);

        assertThrows(RuntimeException.class,
                () -> workOrderService.assignOrder(1L, 10L));
    }

    @Test
    @DisplayName("分配工单 - 正常场景")
    void testAssignOrder_Success() {
        when(workOrderMapper.selectById(1L)).thenReturn(testOrder);
        when(workOrderMapper.updateById(any(WorkOrder.class))).thenReturn(1);

        workOrderService.assignOrder(1L, 10L);
        assertEquals(2, testOrder.getOrderStatus()); // 处理中
        assertEquals(10L, testOrder.getAgentId());
    }

    @Test
    @DisplayName("智能分类 - 退款关键词")
    void testAutoClassify_Refund() {
        testOrder.setOrderContent("我要退款退货");
        when(workOrderMapper.selectById(1L)).thenReturn(testOrder);
        when(workOrderMapper.updateById(any(WorkOrder.class))).thenReturn(1);

        String type = workOrderService.autoClassify(1L);
        assertEquals("退款", type);
    }

    @Test
    @DisplayName("智能分类 - 投诉关键词")
    void testAutoClassify_Complaint() {
        testOrder.setOrderContent("我要投诉你们");
        when(workOrderMapper.selectById(1L)).thenReturn(testOrder);
        when(workOrderMapper.updateById(any(WorkOrder.class))).thenReturn(1);

        String type = workOrderService.autoClassify(1L);
        assertEquals("投诉", type);
    }

    @Test
    @DisplayName("更新状态 - 正常场景")
    void testUpdateStatus() {
        when(workOrderMapper.selectById(1L)).thenReturn(testOrder);
        when(workOrderMapper.updateById(any(WorkOrder.class))).thenReturn(1);

        workOrderService.updateStatus(1L, 3); // 已完成
        assertEquals(3, testOrder.getOrderStatus());
    }
}
