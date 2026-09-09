package com.ai.cs.aiagent.service;

import com.ai.cs.aiagent.enums.AiToolEnum;
import com.ai.cs.api.feign.WorkOrderFeign;
import com.ai.cs.common.dto.WorkOrderDTO;
import com.ai.cs.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import jakarta.annotation.Resource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agent工具增强服务
 * 提供工具链编排、工具缓存、工具权限控制等功能
 *
 * @author huangrenhui
 * @date 2026/7/20
 */
@Slf4j
@Service
public class AgentToolService {

    @Resource
    private WorkOrderFeign workOrderFeign;

    /** 工具执行结果缓存：key 为 "工具名:消息哈希"，避免相同输入重复调用下游服务 */
    private final Map<String, String> toolCache = new ConcurrentHashMap<>();

    /**
     * 获取当前可用的工具列表（供前端或上游展示/选择）
     *
     * @return 工具名称与描述的列表
     */
    public List<Map<String, String>> getAvailableTools() {
        List<Map<String, String>> tools = new ArrayList<>();
        // 遍历工具枚举，逐个组装名称与描述
        for (AiToolEnum tool : AiToolEnum.values()) {
            Map<String, String> info = new HashMap<>();
            info.put("name", tool.getName());
            info.put("description", tool.getDescription());
            tools.add(info);
        }
        return tools;
    }

    /**
     * 顺序执行工具链：逐个调用指定工具，把每个工具的结果按 "[工具名] 结果" 拼成一段文本
     *
     * @param sessionId 会话 ID（透传给需要上下文的工具）
     * @param userMsg   用户消息原文（作为工具输入）
     * @param toolNames 要执行的工具名列表（按顺序执行）
     * @return 各工具执行结果的汇总文本，单个工具失败不影响后续工具
     */
    public String executeToolChain(String sessionId, String userMsg, List<String> toolNames) {
        StringBuilder result = new StringBuilder();
        for (String toolName : toolNames) {
            try {
                String toolResult = executeTool(sessionId, userMsg, toolName);
                result.append("[").append(toolName).append("] ").append(toolResult).append("\n");
            } catch (Exception e) {
                // 单个工具失败只记录失败项，不中断整条链
                result.append("[").append(toolName).append("] 执行失败: ").append(e.getMessage()).append("\n");
            }
        }
        return result.toString();
    }

    /**
     * 执行单个工具（带结果缓存）
     *
     * @param sessionId 会话 ID
     * @param userMsg   用户消息原文
     * @param toolName  工具名（见 {@link AiToolEnum}）
     * @return 工具执行结果文本；未知工具返回提示文案
     */
    public String executeTool(String sessionId, String userMsg, String toolName) {
        // 用消息哈希做缓存键的一部分，相同工具+相同消息直接复用结果
        String cacheKey = toolName + ":" + (userMsg == null ? 0 : userMsg.hashCode());
        String cached = toolCache.get(cacheKey);
        if (cached != null) {
            log.debug("工具执行结果命中缓存: {}", toolName);
            return cached;
        }

        String result = doExecuteTool(sessionId, userMsg, toolName);
        // 缓存无限增长时整体清空，防止内存膨胀（简单的容量保护策略）
        if (toolCache.size() > 200) {
            toolCache.clear();
        }
        toolCache.put(cacheKey, result);
        return result;
    }

    /**
     * 工具路由：根据工具名找到枚举并分发到具体实现
     */
    private String doExecuteTool(String sessionId, String userMsg, String toolName) {
        AiToolEnum tool = AiToolEnum.fromName(toolName);
        if (tool == null) {
            return "未知工具: " + toolName;
        }

        // 按工具类型分支：只有 CREATE_ORDER 真正调用下游服务，其余为占位提示文案
        return switch (tool) {
            case CREATE_ORDER -> createWorkOrder(sessionId, userMsg);
            case QUERY_ORDER -> "工单查询功能: 请输入工单号进行查询";
            case QUERY_FAQ -> "FAQ查询功能: 请描述您的问题";
            case TRANSFER_AGENT -> "已为您转接人工客服，请耐心等待~";
            default -> "工具 " + toolName + " 暂未实现";
        };
    }

    /**
     * 创建咨询类工单：调用工单服务 Feign 接口落单
     *
     * @param sessionId 会话 ID（关联会话便于追溯）
     * @param content   工单内容（通常为用户消息原文）
     * @return 创建结果提示文案，失败时返回失败原因
     */
    private String createWorkOrder(String sessionId, String content) {
        try {
            WorkOrderDTO orderDTO = new WorkOrderDTO();
            orderDTO.setOrderType("咨询");
            orderDTO.setContent(content);
            orderDTO.setSessionId(sessionId);
            // 客户 ID 未知时以 0 占位，由工单服务侧兜底处理
            orderDTO.setCustomerId(0L);
            Result<String> created = workOrderFeign.createOrder(orderDTO);
            if (created != null && created.isOk()) {
                return created.getData() == null ? "工单已创建成功" : created.getData();
            }
            return "创建工单失败: " + (created == null ? "无响应" : created.getMsg());
        } catch (Exception e) {
            log.error("创建工单失败", e);
            return "创建工单失败: " + e.getMessage();
        }
    }

    /**
     * 清空工具结果缓存
     */
    public void clearCache() {
        toolCache.clear();
        log.info("工具缓存已清除");
    }
}
