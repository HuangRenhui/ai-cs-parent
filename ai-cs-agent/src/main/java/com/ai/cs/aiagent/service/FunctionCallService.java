package com.ai.cs.aiagent.service;

import com.ai.cs.api.feign.OpenToolFeign;
import com.ai.cs.common.dto.ToolInvokeDTO;
import com.ai.cs.common.dto.ToolInvokeResultDTO;
import com.ai.cs.common.llm.ModelCallException;
import com.ai.cs.common.llm.ModelRouter;
import com.ai.cs.common.llm.ModelTypeEnum;
import com.ai.cs.common.result.Result;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Function Call 服务：让大模型根据工具 JSON Schema 自动选择并调用工具
 * 补全功能清单 P0 半成品「模型按 Schema 自动选工具未实现」
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

    private static final String FUNCTION_CALL_PROMPT = """
            你是智能客服的工具调度器。根据用户意图和可用工具列表，选择最合适的工具并提取调用参数。

            只输出一个 JSON 对象，不要输出 markdown、解释或其它文字。
            JSON 格式：
            {"tool": "<工具名>", "params": {<参数键值对>}, "needConfirm": true/false}

            规则：
            1. tool 必须是可用工具列表中的工具名
            2. params 按工具的 inputSchema 提取参数值
            3. needConfirm: 风险等级为 write 或 critical 时必须为 true
            4. 如果没有合适的工具，返回 {"tool": "none"}

            可用工具：
            %s

            用户意图：%s
            用户消息：%s
            """;

    /**
     * 模型自动选工具并执行
     *
     * @param userMsg     用户消息
     * @param intentName  意图名称
     * @param sessionId   会话ID
     * @param tenantCode  租户编码
     * @param availableTools 可用工具列表（从OpenTool注册表获取）
     * @return 工具执行结果；无合适工具时返回null
     */
    public String autoSelectAndExecute(String userMsg, String intentName, String sessionId,
                                        String tenantCode, List<Map<String, Object>> availableTools) {
        if (availableTools == null || availableTools.isEmpty()) {
            log.debug("无可用工具，跳过Function Call");
            return null;
        }

        String toolsJson = JSON.toJSONString(availableTools);
        String prompt = String.format(FUNCTION_CALL_PROMPT, toolsJson, intentName, userMsg);

        try {
            String response = modelRouter.chatForType(ModelTypeEnum.LLM.getCode(),
                    List.of(Map.of("role", "user", "content", prompt)));
            JSONObject decision = parseFunctionCallResponse(response);
            if (decision == null || "none".equals(decision.getString("tool"))) {
                log.info("模型未选择任何工具 intent={}", intentName);
                return null;
            }

            String toolName = decision.getString("tool");
            JSONObject params = decision.getJSONObject("params");
            boolean needConfirm = decision.getBooleanValue("needConfirm");

            return executeSelectedTool(toolName, params, needConfirm, sessionId, tenantCode);
        } catch (ModelCallException e) {
            log.error("Function Call模型调用失败", e);
            return null;
        }
    }

    /**
     * 构建工具的Function定义（供OpenAI兼容协议的tools参数使用）
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

    /**
     * 解析模型输出的Function Call决策
     */
    private JSONObject parseFunctionCallResponse(String response) {
        if (response == null || response.isBlank()) {
            return null;
        }
        String text = response.trim();
        if (text.startsWith("```")) {
            int start = text.indexOf('{');
            int end = text.lastIndexOf('}');
            if (start >= 0 && end > start) {
                text = text.substring(start, end + 1);
            }
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            try {
                return JSON.parseObject(text.substring(start, end + 1));
            } catch (Exception e) {
                log.warn("Function Call响应解析失败: {}", text);
                return null;
            }
        }
        return null;
    }

    /**
     * 执行模型选中的工具
     */
    private String executeSelectedTool(String toolName, JSONObject params, boolean needConfirm,
                                        String sessionId, String tenantCode) {
        if (openToolFeign == null) {
            return null;
        }
        try {
            ToolInvokeDTO invoke = new ToolInvokeDTO();
            invoke.setIntentBind(toolName);
            invoke.setSessionId(sessionId);
            invoke.setConfirmed(!needConfirm);
            if (params != null) {
                String entityId = params.getString("entityId");
                if (entityId == null) {
                    entityId = params.getString("orderId");
                }
                if (entityId == null) {
                    entityId = params.getString("orderNo");
                }
                invoke.setEntityId(entityId);
            }
            if (StringUtils.hasText(sessionId) && StringUtils.hasText(toolName)) {
                invoke.setIdempotencyKey(sessionId + ":" + toolName + ":" + (invoke.getEntityId() != null ? invoke.getEntityId() : "0"));
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
            log.warn("Function Call工具执行失败: tool={}, error={}", toolName, e.getMessage());
        }
        return null;
    }
}