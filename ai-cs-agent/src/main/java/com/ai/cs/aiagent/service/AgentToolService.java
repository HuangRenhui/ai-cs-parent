package com.ai.cs.aiagent.service;

import com.ai.cs.aiagent.enums.AiToolEnum;
import com.ai.cs.api.feign.OpenToolFeign;
import com.ai.cs.api.feign.WorkOrderFeign;
import com.ai.cs.common.dto.ToolInvokeDTO;
import com.ai.cs.common.dto.ToolInvokeResultDTO;
import com.ai.cs.common.dto.WorkOrderDTO;
import com.ai.cs.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import jakarta.annotation.Resource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

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

    @Resource
    private OpenToolFeign openToolFeign;

    @Resource
    private RedisTemplate<String, String> redisTemplate;

    private static final String TOOL_CACHE_PREFIX = "tool:cache:";
    private static final long TOOL_CACHE_TTL_MINUTES = 5;

    /** 本地进程内缓存（Redis不可用时的降级方案） */
    private final Map<String, String> localCache = new ConcurrentHashMap<>();

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
        String cacheKey = toolName + ":" + (userMsg == null ? 0 : userMsg.hashCode());
        String cached = getFromCache(cacheKey);
        if (cached != null) {
            log.debug("工具执行结果命中缓存: {}", toolName);
            return cached;
        }

        String result = doExecuteTool(sessionId, userMsg, toolName);
        putToCache(cacheKey, result);
        return result;
    }

    /**
     * 工具路由：根据工具名找到枚举并分发到具体实现
     */
    private String doExecuteTool(String sessionId, String userMsg, String toolName) {
        AiToolEnum tool = AiToolEnum.fromName(toolName);
        if (tool == null) {
            String openResult = invokeOpenTool(toolName, userMsg, sessionId);
            return openResult != null ? openResult : "未知工具: " + toolName;
        }

        return switch (tool) {
            case CREATE_ORDER -> createWorkOrder(sessionId, userMsg);
            case QUERY_ORDER -> invokeOpenToolOrFallback("queryOrder", userMsg, sessionId, "工单查询功能: 请输入工单号进行查询");
            case QUERY_LOGISTICS -> invokeOpenToolOrFallback("queryLogistics", userMsg, sessionId, "物流查询功能: 请提供订单号查询物流进度");
            case QUERY_FAQ -> "FAQ查询功能: 请描述您的问题";
            case TRANSFER_AGENT -> "已为您转接人工客服，请耐心等待~";
            case QUERY_KNOWLEDGE -> "知识库查询: 请描述您的问题";
            default -> invokeOpenToolOrFallback(toolName, userMsg, sessionId, "工具 " + toolName + " 暂未实现");
        };
    }

    /**
     * 优先调用开放工具，失败时返回降级文案
     */
    private String invokeOpenToolOrFallback(String toolName, String userMsg, String sessionId, String fallback) {
        String result = invokeOpenTool(toolName, userMsg, sessionId);
        return result != null ? result : fallback;
    }

    /**
     * 调用开放工具平台（接入主对话链路）
     */
    private String invokeOpenTool(String toolName, String userMsg, String sessionId) {
        if (openToolFeign == null) {
            return null;
        }
        try {
            ToolInvokeDTO invoke = new ToolInvokeDTO();
            invoke.setIntentBind(toolName);
            invoke.setEntityId(userMsg);
            invoke.setSessionId(sessionId);
            invoke.setConfirmed(false);
            if (StringUtils.hasText(sessionId)) {
                invoke.setIdempotencyKey(sessionId + ":" + toolName + ":" + (userMsg == null ? 0 : userMsg.hashCode()));
            }
            Result<ToolInvokeResultDTO> res = openToolFeign.invoke(invoke);
            if (res != null && res.isOk() && res.getData() != null) {
                ToolInvokeResultDTO data = res.getData();
                if (data.isSuccess() && StringUtils.hasText(data.getOutput())) {
                    return data.getOutput();
                }
                if (!data.isSuccess() && StringUtils.hasText(data.getOutput())) {
                    return data.getOutput();
                }
            }
        } catch (Exception e) {
            log.warn("开放工具调用失败: tool={}, error={}", toolName, e.getMessage());
        }
        return null;
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
        localCache.clear();
        log.info("工具本地缓存已清除");
    }

    private String getFromCache(String cacheKey) {
        if (redisTemplate != null) {
            try {
                return redisTemplate.opsForValue().get(TOOL_CACHE_PREFIX + cacheKey);
            } catch (Exception e) {
                log.debug("Redis工具缓存读取失败，降级到本地缓存");
            }
        }
        return localCache.get(cacheKey);
    }

    private void putToCache(String cacheKey, String value) {
        if (redisTemplate != null) {
            try {
                redisTemplate.opsForValue().set(TOOL_CACHE_PREFIX + cacheKey, value,
                        TOOL_CACHE_TTL_MINUTES, TimeUnit.MINUTES);
                return;
            } catch (Exception e) {
                log.debug("Redis工具缓存写入失败，降级到本地缓存");
            }
        }
        if (localCache.size() > 200) {
            localCache.clear();
        }
        localCache.put(cacheKey, value);
    }
}