package com.ai.cs.workorder.controller;

import com.ai.cs.common.dto.WorkOrderDTO;
import com.ai.cs.common.result.Result;
import com.ai.cs.workorder.entity.WorkOrder;
import com.ai.cs.workorder.service.WorkOrderService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 工单控制器（完整CRUD + 智能处理）
 *
 * @author huangrenhui
 * @date 2026/6/11 18:17
 */
@RestController
@RequestMapping("/workorder")
public class WorkOrderController {

    @Resource
    private WorkOrderService workOrderService;

    /** 创建工单（校验参数后生成 WO_ 前缀工单号，初始状态为待处理） */
    @PostMapping("/create")
    public Result<String> create(@Valid @RequestBody WorkOrderDTO dto) {
        String orderNo = workOrderService.createOrder(dto);
        return Result.success("工单创建成功，工单号：" + orderNo);
    }

    /** 分页查询工单列表 */
    @GetMapping("/page")
    public Result<Page<WorkOrder>> page(@RequestParam(defaultValue = "1") int pageNum,
                                         @RequestParam(defaultValue = "10") int pageSize,
                                         @RequestParam(required = false) String orderType,
                                         @RequestParam(required = false) Integer orderStatus,
                                         @RequestParam(required = false) Long agentId,
                                         @RequestParam(required = false) String keyword) {
        return Result.success(workOrderService.queryPage(pageNum, pageSize, orderType, orderStatus, agentId, keyword));
    }

    /** 获取工单详情 */
    @GetMapping("/info/{id}")
    public Result<WorkOrder> info(@PathVariable Long id) {
        return Result.success(workOrderService.getById(id));
    }

    /** 更新工单 */
    @PutMapping("/update")
    public Result<String> update(@RequestBody WorkOrder order) {
        workOrderService.updateById(order);
        return Result.success("更新成功");
    }

    /** 分配工单给坐席 */
    @PutMapping("/assign/{id}")
    public Result<String> assign(@PathVariable Long id, @RequestParam Long agentId) {
        workOrderService.assignOrder(id, agentId);
        return Result.success("分配成功");
    }

    /** 更新工单状态 */
    @PutMapping("/status/{id}")
    public Result<String> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        workOrderService.updateStatus(id, status);
        return Result.success("状态更新成功");
    }

    /** 完成工单（状态置为 3-已完成） */
    @PutMapping("/complete/{id}")
    public Result<String> complete(@PathVariable Long id) {
        workOrderService.updateStatus(id, 3);
        return Result.success("工单已完成");
    }

    /** 关闭工单（状态置为 4-已关闭，关闭为终态不再流转） */
    @PutMapping("/close/{id}")
    public Result<String> close(@PathVariable Long id) {
        workOrderService.updateStatus(id, 4);
        return Result.success("工单已关闭");
    }

    /** 删除工单 */
    @DeleteMapping("/delete/{id}")
    public Result<String> delete(@PathVariable Long id) {
        workOrderService.removeById(id);
        return Result.success("删除成功");
    }

    /** 智能推荐相似工单 */
    @GetMapping("/similar/{id}")
    public Result<List<WorkOrder>> similarOrders(@PathVariable Long id) {
        return Result.success(workOrderService.findSimilarOrders(id));
    }

    /** 工单智能分类 */
    @PostMapping("/auto-classify/{id}")
    public Result<String> autoClassify(@PathVariable Long id) {
        String category = workOrderService.autoClassify(id);
        return Result.success("自动分类结果: " + category);
    }
}
