package com.ai.cs.aiagent.service;

import com.ai.cs.aiagent.enums.AiToolEnum;
import com.ai.cs.api.feign.OpenToolFeign;
import com.ai.cs.api.feign.WorkOrderFeign;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import jakarta.annotation.Resource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agent 工具增强服务（占位）：工具链编排、工具路由、结果缓存
 *
 * <p>TODO 后续实现：按工具名分发到具体实现（内置 {@link AiToolEnum} 分支 + 未命中时透传开放工具平台），
 * 开放工具经 {@code OpenToolFeign.invoke} 调用并以 {@code sessionId:tool:hash} 做幂等键；
 * 创建工单走 {@code WorkOrderFeign}；结果按 {@code tool:cache:} 前缀写入 Redis（TTL 5 分钟），
 * Redis 不可用时降级为进程内缓存（超过 200 条整体清空）。</p>
 *
 * <p>当前 {@link #executeToolChain} 返回空串、{@link #executeTool} 恒返回 null：
 * 不调用任何模型或外部服务，也不写缓存。{@link #getAvailableTools} 与 {@link #clearCache} 已实现。</p>
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
     * 顺序执行工具链（占位：不执行任何工具，返回空串）
     *
     * <p>TODO 后续实现：逐个调用指定工具，把结果按 {@code [工具名] 结果} 拼段累加，
     * 单个工具失败只记录失败项、不中断整条链。</p>
     *
     * @param sessionId 会话 ID
     * @param userMsg   用户消息原文
     * @param toolNames 要执行的工具名列表
     * @return 空串
     */
    public String executeToolChain(String sessionId, String userMsg, List<String> toolNames) {
        log.info("[占位] 工具链执行未实现 toolNames={}，返回空串", toolNames);
        return "";
    }

    /**
     * 执行单个工具（占位：不执行、不查缓存，恒返回 null）
     *
     * @param sessionId 会话 ID
     * @param userMsg   用户消息原文
     * @param toolName  工具名
     * @return null
     */
    public String executeTool(String sessionId, String userMsg, String toolName) {
        log.info("[占位] 工具执行未实现 toolName={}，返回 null", toolName);
        return null;
    }

    /**
     * 清空工具结果缓存
     */
    public void clearCache() {
        localCache.clear();
        log.info("工具本地缓存已清除");
    }
}
