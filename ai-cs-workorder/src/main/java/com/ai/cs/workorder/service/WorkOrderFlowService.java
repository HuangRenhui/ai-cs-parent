package com.ai.cs.workorder.service;

import com.ai.cs.common.exception.BusinessException;
import com.ai.cs.workorder.entity.WorkOrder;
import com.ai.cs.workorder.mapper.WorkOrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.*;

/**
 * Flowable 工单流程核心服务
 * 管理工单流程的生命周期
 *
 * @author huangrenhui
 * @date 2026/7/20
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "flowable.enabled", havingValue = "true", matchIfMissing = false)
public class WorkOrderFlowService {

    /** BPMN 流程定义 Key，对应流程定义文件中的 process id */
    private static final String PROCESS_DEFINITION_KEY = "workOrderProcess";

    @Resource
    private RuntimeService runtimeService;

    @Resource
    private TaskService taskService;

    @Resource
    private HistoryService historyService;

    @Resource
    private RepositoryService repositoryService;

    @Resource
    private WorkOrderMapper workOrderMapper;

    /**
     * 启动工单流程
     *
     * @param workOrderId 工单ID
     * @return 包含流程实例ID、工单ID和启动状态的结果
     */
    public Map<String, Object> startProcess(Long workOrderId) {
        WorkOrder order = workOrderMapper.selectById(workOrderId);
        if (order == null) {
            throw new BusinessException(404, "工单不存在");
        }

        // 设置流程变量：供 BPMN 中的网关条件与任务分配表达式使用
        Map<String, Object> variables = new HashMap<>();
        variables.put("workOrderId", workOrderId);
        variables.put("orderType", order.getOrderType());
        // 已分配坐席则指派给具体坐席，否则进入坐席池待领取
        variables.put("assignee", "agent_" + (order.getAgentId() != null ? order.getAgentId() : "pool"));
        variables.put("reviewer", "reviewer_pool");

        // 按流程定义 Key 启动实例，业务键采用 "WO_工单ID"，便于后续反查
        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey(
                PROCESS_DEFINITION_KEY, "WO_" + workOrderId, variables);

        // 更新工单状态为处理中
        order.setOrderStatus(2);
        workOrderMapper.updateById(order);

        Map<String, Object> result = new HashMap<>();
        result.put("processInstanceId", processInstance.getId());
        result.put("workOrderId", workOrderId);
        result.put("status", "started");

        log.info("[Flowable] 工单 {} 流程已启动, 流程实例ID: {}", workOrderId, processInstance.getId());
        return result;
    }

    /**
     * 查询工单当前流程状态
     *
     * @param workOrderId 工单ID
     * @return status 为 running（含当前任务列表）/ completed（含结束时间）/ not_started 三种之一
     */
    public Map<String, Object> getProcessStatus(Long workOrderId) {
        Map<String, Object> result = new HashMap<>();

        // 查询运行中的流程实例
        List<ProcessInstance> instances = runtimeService.createProcessInstanceQuery()
                .processInstanceBusinessKey("WO_" + workOrderId)
                .list();

        if (!instances.isEmpty()) {
            ProcessInstance instance = instances.get(0);
            result.put("status", "running");
            result.put("processInstanceId", instance.getId());

            // 查询当前任务
            List<Task> tasks = taskService.createTaskQuery()
                    .processInstanceId(instance.getId())
                    .list();
            List<Map<String, Object>> taskList = new ArrayList<>();
            for (Task task : tasks) {
                Map<String, Object> taskInfo = new HashMap<>();
                taskInfo.put("taskId", task.getId());
                taskInfo.put("taskName", task.getName());
                taskInfo.put("assignee", task.getAssignee());
                taskInfo.put("createTime", task.getCreateTime());
                taskList.add(taskInfo);
            }
            result.put("currentTasks", taskList);
        } else {
            // 查询历史流程
            HistoricProcessInstance historic = historyService.createHistoricProcessInstanceQuery()
                    .processInstanceBusinessKey("WO_" + workOrderId)
                    .singleResult();
            if (historic != null) {
                result.put("status", "completed");
                result.put("processInstanceId", historic.getId());
                result.put("endTime", historic.getEndTime());
            } else {
                result.put("status", "not_started");
            }
        }

        return result;
    }

    /**
     * 完成指定任务（审批/处理动作，驱动流程向下流转）
     *
     * @param taskId    流程任务ID
     * @param variables 流程变量；质检审核任务若未显式传 qualityPassed，则默认置为 true 直接通过
     */
    public void completeTask(String taskId, Map<String, Object> variables) {
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) {
            throw new BusinessException(404, "任务不存在或已完成");
        }

        if (variables == null) {
            variables = new HashMap<>();
        }

        // 质检任务默认通过
        if ("质检审核".equals(task.getName()) && !variables.containsKey("qualityPassed")) {
            variables.put("qualityPassed", true);
        }

        taskService.complete(taskId, variables);
        log.info("[Flowable] 任务 {} ({}) 已完成", taskId, task.getName());
    }

    /**
     * 获取待办任务列表
     *
     * @param assignee 处理人标识；为空时返回全部待办，按创建时间倒序
     * @return 待办任务列表
     */
    public List<Map<String, Object>> getPendingTasks(String assignee) {
        List<Task> tasks;
        if (assignee != null && !assignee.isEmpty()) {
            tasks = taskService.createTaskQuery()
                    .taskAssignee(assignee)
                    .orderByTaskCreateTime().desc()
                    .list();
        } else {
            tasks = taskService.createTaskQuery()
                    .orderByTaskCreateTime().desc()
                    .list();
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Task task : tasks) {
            Map<String, Object> taskInfo = new HashMap<>();
            taskInfo.put("taskId", task.getId());
            taskInfo.put("taskName", task.getName());
            taskInfo.put("assignee", task.getAssignee());
            taskInfo.put("processInstanceId", task.getProcessInstanceId());
            taskInfo.put("createTime", task.getCreateTime());
            result.add(taskInfo);
        }

        return result;
    }

    /**
     * 获取流程定义列表（仅返回每个 Key 的最新版本）
     *
     * @return 流程定义列表（id/key/name/version）
     */
    public List<Map<String, Object>> getProcessDefinitions() {
        List<ProcessDefinition> definitions = repositoryService.createProcessDefinitionQuery()
                .latestVersion()
                .list();

        List<Map<String, Object>> result = new ArrayList<>();
        for (ProcessDefinition def : definitions) {
            Map<String, Object> info = new HashMap<>();
            info.put("id", def.getId());
            info.put("key", def.getKey());
            info.put("name", def.getName());
            info.put("version", def.getVersion());
            result.add(info);
        }

        return result;
    }

    /**
     * 获取流程历史记录
     *
     * @param workOrderId 工单ID
     * @return 该工单流程实例下全部历史任务（按开始时间升序）；流程未启动时返回空列表
     */
    public List<Map<String, Object>> getProcessHistory(Long workOrderId) {
        HistoricProcessInstance historic = historyService.createHistoricProcessInstanceQuery()
                .processInstanceBusinessKey("WO_" + workOrderId)
                .singleResult();

        if (historic == null) {
            return Collections.emptyList();
        }

        // 查询历史任务
        List<org.flowable.task.api.history.HistoricTaskInstance> tasks =
                historyService.createHistoricTaskInstanceQuery()
                        .processInstanceId(historic.getId())
                        .orderByHistoricTaskInstanceStartTime().asc()
                        .list();

        List<Map<String, Object>> result = new ArrayList<>();
        for (var task : tasks) {
            Map<String, Object> info = new HashMap<>();
            info.put("taskId", task.getId());
            info.put("taskName", task.getName());
            info.put("assignee", task.getAssignee());
            info.put("startTime", task.getStartTime());
            info.put("endTime", task.getEndTime());
            result.add(info);
        }

        return result;
    }

    /**
     * 取消工单流程
     *
     * @param workOrderId 工单ID
     * @param reason      取消原因（会记录到流程实例的删除原因中）
     */
    public void cancelProcess(Long workOrderId, String reason) {
        // 删除该工单下所有仍在运行的流程实例
        List<ProcessInstance> instances = runtimeService.createProcessInstanceQuery()
                .processInstanceBusinessKey("WO_" + workOrderId)
                .list();

        for (ProcessInstance instance : instances) {
            runtimeService.deleteProcessInstance(instance.getId(), reason);
        }

        // 更新工单状态
        WorkOrder order = workOrderMapper.selectById(workOrderId);
        if (order != null) {
            order.setOrderStatus(0); // 已取消
            workOrderMapper.updateById(order);
        }

        log.info("[Flowable] 工单 {} 流程已取消, 原因: {}", workOrderId, reason);
    }
}