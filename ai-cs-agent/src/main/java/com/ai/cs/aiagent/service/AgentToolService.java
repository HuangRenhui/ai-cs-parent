package com.ai.cs.aiagent.service;

import com.ai.cs.aiagent.enums.AiToolEnum;
import com.ai.cs.api.feign.WorkOrderFeign;
import com.ai.cs.common.dto.WorkOrderDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import jakarta.annotation.Resource;

import java.util.*;

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

    /** 工具执行结果缓存 */
    private final Map<String, Object> toolCache = new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Object> eldest) {
            return size() > 100;
        }
    };

    /**
     * 获取所有可用工具列表
     */
    public List<Map<String, String>> getAvailableTools() {
        List<Map<String, String>> tools = new ArrayList<>();
        for (AiToolEnum tool : AiToolEnum.values()) {
            Map<String, String> info = new HashMap<>();
            info.put("name", tool.getName());
            info.put("description", tool.getDescription());
            tools.add(info);
        }
        return tools;
    }

    /**
     * 工具链编排：按顺序执行多个工具
     * @param sessionId 会话ID
     * @param userMsg 用户消息
     * @param toolNames 工具名称列表
     * @return 执行结果汇总
     */
    public String executeToolChain(String sessionId, String userMsg, List<String> toolNames) {
        StringBuilder result = new StringBuilder();
        for (String toolName : toolNames) {
            try {
                String toolResult = executeTool(sessionId, userMsg, toolName);
                result.append("[").append(toolName).append("] ").append(toolResult).append("\n");
            } catch (Exception e) {
                result.append("[").append(toolName).append("] 执行失败: ").append(e.getMessage()).append("\n");
            }
        }
        return result.toString();
    }

    /**
     * 执行单个工具（带缓存）
     */
    public String executeTool(String sessionId, String userMsg, String toolName) {
        String cacheKey = toolName + ":" + userMsg.hashCode();
        if (toolCache.containsKey(cacheKey)) {
            log.debug("工具执行结果命中缓存: {}", toolName);
            return toolCache.get(cacheKey).toString();
        }

        String result = doExecuteTool(sessionId, userMsg, toolName);
        toolCache.put(cacheKey, result);
        return result;
    }

    private String doExecuteTool(String sessionId, String userMsg, String toolName) {
        AiToolEnum tool = AiToolEnum.fromName(toolName);
        if (tool == null) {
            return "未知工具: " + toolName;
        }

        return switch (tool) {
            case CREATE_ORDER -> createWorkOrder(sessionId, userMsg);
            case QUERY_ORDER -> "工单查询功能: 请输入工单号进行查询";
            case QUERY_FAQ -> "FAQ查询功能: 请描述您的问题";
            case TRANSFER_AGENT -> "已为您转接人工客服，请耐心等待~";
            default -> "工具 " + toolName + " 暂未实现";
        };
    }

    private String createWorkOrder(String sessionId, String content) {
        try {
            WorkOrderDTO orderDTO = new WorkOrderDTO();
            orderDTO.setOrderType("咨询");
            orderDTO.setContent(content);
            orderDTO.setSessionId(sessionId);
            orderDTO.setCustomerId(0L);
            workOrderFeign.createOrder(orderDTO);
            return "工单已创建成功";
        } catch (Exception e) {
            log.error("创建工单失败", e);
            return "创建工单失败: " + e.getMessage();
        }
    }

    /**
     * 清除工具缓存
     */
    public void clearCache() {
        toolCache.clear();
        log.info("工具缓存已清除");
    }
}
