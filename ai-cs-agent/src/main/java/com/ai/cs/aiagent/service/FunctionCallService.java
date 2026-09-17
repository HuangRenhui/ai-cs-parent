package com.ai.cs.aiagent.service;

import com.ai.cs.api.feign.OpenToolFeign;
import com.ai.cs.common.llm.ModelRouter;
import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Function Call 服务（占位）：让大模型根据工具 JSON Schema 自动选择并调用工具
 *
 * <p>TODO 后续实现：把可用工具序列化后填入调度器提示词，调用 LLM 得到
 * {@code {"tool","params","needConfirm"}} 决策并解析（兼容 ```json 代码块）；
 * 风险等级为 write/critical 时必须确认（{@code confirmed=!needConfirm}）；
 * 再经 {@code OpenToolFeign.invoke} 执行，用 {@code sessionId:tool:entityId} 做幂等键。
 * 模型未选中工具或无合适工具时返回 null。</p>
 *
 * <p>当前不调用模型：{@link #autoSelectAndExecute} 恒返回 null（无工具被选中执行）。</p>
 *
 * @author huangrenhui
 */
@Slf4j
@Service
public class FunctionCallService {

    @Resource
    private ModelRouter modelRouter;

    @Resource
    private OpenToolFeign openToolFeign;

    /**
     * 模型自动选工具并执行（占位：恒返回 null，不选工具、不执行）
     *
     * @param userMsg        用户消息
     * @param intentName     意图名称
     * @param sessionId      会话ID
     * @param tenantCode     租户编码
     * @param availableTools 可用工具列表
     * @return null
     */
    public String autoSelectAndExecute(String userMsg, String intentName, String sessionId,
                                        String tenantCode, List<Map<String, Object>> availableTools) {
        log.info("[占位] Function Call 自动选工具未实现 intent={}，可用工具数={}，返回 null",
                intentName, availableTools == null ? 0 : availableTools.size());
        return null;
    }

    /**
     * 构建工具的 Function 定义（供 OpenAI 兼容协议的 tools 参数使用）
     *
     * <p>纯结构映射，不依赖模型与外部服务，保留为真实现。</p>
     */
    public List<Map<String, Object>> buildToolDefinitions(List<Map<String, Object>> registeredTools) {
        List<Map<String, Object>> definitions = new ArrayList<>();
        for (Map<String, Object> tool : registeredTools) {
            Map<String, Object> function = new HashMap<>();
            function.put("name", tool.get("name"));
            function.put("description", tool.get("description"));
            if (tool.get("inputSchema") != null) {
                try {
                    function.put("parameters", JSON.parseObject(String.valueOf(tool.get("inputSchema"))));
                } catch (Exception e) {
                    function.put("parameters", Map.of("type", "object"));
                }
            } else {
                function.put("parameters", Map.of("type", "object"));
            }
            definitions.add(Map.of("type", "function", "function", function));
        }
        return definitions;
    }
}
