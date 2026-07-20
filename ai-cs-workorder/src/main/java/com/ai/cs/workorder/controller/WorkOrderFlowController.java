package com.ai.cs.workorder.controller;

import com.ai.cs.common.result.Result;
import com.ai.cs.workorder.service.WorkOrderFlowService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.util.Map;

/**
 * Flowable 工单流程控制器
 * 提供流程启动、审批、查询等接口
 *
 * @author huangrenhui
 * @date 2026/7/20
 */
@Slf4j
@RestController
@RequestMapping("/workorder/flow")
@ConditionalOnProperty(name = "flowable.enabled", havingValue = "true", matchIfMissing = false)
public class WorkOrderFlowController {

    @Resource
    private WorkOrderFlowService workOrderFlowService;

    /**
     * 启动工单流程
     */
    @PostMapping("/start/{workOrderId}")
    public Result<Map<String, Object>> startProcess(@PathVariable Long workOrderId) {
        Map<String, Object> result = workOrderFlowService.startProcess(workOrderId);
        return Result.success(result);
    }

    /**
     * 查询工单当前流程状态
     */
    @GetMapping("/status/{workOrderId}")
    public Result<Map<String, Object>> getProcessStatus(@PathVariable Long workOrderId) {
        Map<String, Object> result = workOrderFlowService.getProcessStatus(workOrderId);
        return Result.success(result);
    }

    /**
     * 完成任务（审批/处理）
     */
    @PostMapping("/complete/{taskId}")
    public Result<String> completeTask(@PathVariable String taskId,
                                        @RequestBody Map<String, Object> variables) {
        workOrderFlowService.completeTask(taskId, variables);
        return Result.success("任务已完成");
    }

    /**
     * 查询待办任务列表
     */
    @GetMapping("/tasks")
    public Result<Object> getPendingTasks(@RequestParam(required = false) String assignee) {
        return Result.success(workOrderFlowService.getPendingTasks(assignee));
    }

    /**
     * 查询流程定义列表
     */
    @GetMapping("/definitions")
    public Result<Object> getProcessDefinitions() {
        return Result.success(workOrderFlowService.getProcessDefinitions());
    }

    /**
     * 获取流程历史记录
     */
    @GetMapping("/history/{workOrderId}")
    public Result<Object> getProcessHistory(@PathVariable Long workOrderId) {
        return Result.success(workOrderFlowService.getProcessHistory(workOrderId));
    }

    /**
     * 取消工单流程
     */
    @PostMapping("/cancel/{workOrderId}")
    public Result<String> cancelProcess(@PathVariable Long workOrderId, @RequestParam String reason) {
        workOrderFlowService.cancelProcess(workOrderId, reason);
        return Result.success("流程已取消");
    }
}
