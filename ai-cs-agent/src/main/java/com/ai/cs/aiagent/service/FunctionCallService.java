package com.ai.cs.aiagent.service;

import com.ai.cs.aiagent.dto.ToolCallOutcome;
import com.ai.cs.api.feign.OpenToolFeign;
import com.ai.cs.common.constant.PromptConst;
import com.ai.cs.common.dto.ToolConfirmCardDTO;
import com.ai.cs.common.dto.ToolInvokeDTO;
import com.ai.cs.common.dto.ToolInvokeResultDTO;
import com.ai.cs.common.llm.ModelRouter;
import com.ai.cs.common.llm.ModelTypeEnum;
import com.ai.cs.common.result.Result;
import com.ai.cs.common.util.ToolInvokeSnapshot;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Function Call 服务：模型按注册表 Schema 选工具，执行权始终在服务端。
 *
 * <p><b>安全底线（实现时不得回退）</b>：模型输出的 needConfirm / confirmed 一律忽略；
 * write/critical 工具由 open 侧第 4 关挂起等人确认，AI 永远不能自行执行。</p>
 */
@Slf4j
@Service
public class FunctionCallService {

    /** 调度器提示词模板：要求只输出 {"tool","params"}，明确模型无执行权 */
    private static final String FUNCTION_CALL_PROMPT = """
            你是智能客服的工具调度器。根据用户消息与可用工具定义，判断是否需要调用工具并提取参数。

            只输出一个 JSON 对象，不要输出 markdown、解释或其它文字：
            {"tool":"<工具名>","params":{<参数键值对>}}

            规则：
            1. tool 必须是「可用工具」中出现的名称；没有合适工具时只输出 {"tool":"none"}。
            2. params 严格按工具的 inputSchema 提取；缺失的信息不要编造。
            3. 你没有执行权限：不要输出 confirmed / needConfirm 字段，
               是否执行、是否需要人工确认由服务端按工具风险等级决定。
            4. 单号、金额等关键参数只能从用户消息原文提取，不要推测。

            可用工具：%s
            当前意图：%s
            用户消息：%s
            """;

    /** 单次喂给模型的工具上限，控制 prompt 体积与成本 */
    private static final int MAX_TOOLS = 12;
    private static final int MAX_SCHEMA_CHARS = 1500;
    private static final int MAX_PARAMS_CHARS = 2048;
    private static final int MAX_SUMMARY_CHARS = 120;

    /** 确认卡只展示白名单字段 */
    private static final List<String> SUMMARY_KEYS =
            List.of("entityId", "orderId", "orderNo", "amount", "couponNo", "reason");

    private static final Map<String, String> SUMMARY_LABELS = Map.of(
            "entityId", "单号", "orderId", "订单号", "orderNo", "订单号",
            "amount", "金额", "couponNo", "券号", "reason", "原因");

    @Value("${ai.agent.function-call.enabled:true}")
    private boolean enabled;

    @Resource
    private ModelRouter modelRouter;

    @Resource
    private OpenToolFeign openToolFeign;

    /**
     * 自动选工具并执行；未选中/任何异常都返回 selected=false，由上层走 RAG 兜底。
     */
    public ToolCallOutcome autoSelectAndExecute(String userMsg, String intentName, String sessionId,
                                                String tenantCode, String requestedBy,
                                                List<Map<String, Object>> availableTools) {
        if (!enabled || !StringUtils.hasText(userMsg)) {
            return ToolCallOutcome.none();
        }
        try {
            List<Map<String, Object>> tools = normalizeTools(availableTools);
            if (tools.isEmpty()) {
                return ToolCallOutcome.none();
            }
            String prompt = PromptConst.fill(FUNCTION_CALL_PROMPT, JSON.toJSONString(tools),
                    intentName == null ? "" : intentName, userMsg);
            String raw = modelRouter.chatForType(ModelTypeEnum.LLM.getCode(),
                    List.of(Map.of("role", "user", "content", prompt)), sessionId);
            JSONObject decision = parseDecision(raw);
            if (decision == null) {
                return ToolCallOutcome.none();
            }
            String toolName = decision.getString("tool");
            if (!StringUtils.hasText(toolName) || "none".equalsIgnoreCase(toolName.trim())) {
                return ToolCallOutcome.none();
            }
            // 防幻觉：工具名必须来自注册白名单，否则整轮放弃
            String chosen = toolName.trim();
            boolean registered = tools.stream()
                    .anyMatch(t -> chosen.equals(String.valueOf(t.get("name"))));
            if (!registered) {
                log.warn("模型选择了未注册工具，已忽略 nameLength={}", chosen.length());
                return ToolCallOutcome.none();
            }
            Map<String, Object> params = decision.getJSONObject("params");
            ToolInvokeDTO invoke = buildInvoke(chosen, params, sessionId, tenantCode, requestedBy);

            Result<ToolInvokeResultDTO> response = openToolFeign.invoke(invoke);
            ToolInvokeResultDTO data = response == null || !response.isOk() ? null : response.getData();
            if (data == null) {
                return ToolCallOutcome.none();
            }
            ToolCallOutcome outcome = new ToolCallOutcome();
            outcome.setSelected(true);
            outcome.setReply(StringUtils.hasText(data.getOutput())
                    ? data.getOutput() : PromptConst.TOOL_BUSY_REPLY);
            if (requiresConfirm(data)) {
                outcome.setConfirmCard(buildCard(data, summaryOf(params), requestedBy));
            }
            return outcome;
        } catch (Exception e) {
            // 日志只记长度，不记用户问题原文
            log.warn("Function Call 执行失败 intent={} sessionId={} msgLength={}",
                    intentName, sessionId, userMsg.length());
            return ToolCallOutcome.none();
        }
    }

    /**
     * 从 open 注册表加载工具；失败返回空列表，不阻断对话主流程。
     */
    public List<Map<String, Object>> loadRegisteredTools() {
        try {
            Result<List<Map<String, Object>>> response = openToolFeign.listTools();
            if (response == null || !response.isOk() || response.getData() == null) {
                return List.of();
            }
            return normalizeTools(response.getData());
        } catch (Exception e) {
            log.warn("加载开放工具注册表失败，跳过 Function Call");
            return List.of();
        }
    }

    /** 构建 OpenAI tools 定义（纯结构映射，已实现，保留）。 */
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

    /** 只保留 name 非空的工具，description 兜底为 name，inputSchema 截断，最多 MAX_TOOLS 条。 */
    private List<Map<String, Object>> normalizeTools(List<Map<String, Object>> source) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> tool : source) {
            if (tool == null) {
                continue;
            }
            String name = tool.get("name") == null ? null : String.valueOf(tool.get("name")).trim();
            if (!StringUtils.hasText(name)) {
                continue;
            }
            String description = tool.get("description") == null
                    ? null : String.valueOf(tool.get("description")).trim();
            String schema = tool.get("inputSchema") == null
                    ? "" : String.valueOf(tool.get("inputSchema"));
            Map<String, Object> item = new HashMap<>();
            item.put("name", name);
            item.put("description", StringUtils.hasText(description) ? description : name);
            item.put("inputSchema", schema.length() > MAX_SCHEMA_CHARS
                    ? schema.substring(0, MAX_SCHEMA_CHARS) : schema);
            result.add(item);
            if (result.size() >= MAX_TOOLS) {
                break;
            }
        }
        return result;
    }

    /** 大括号 depth 配对抠出完整 JSON 对象，兼容 ```json 围栏；解析失败返回 null。 */
    private JSONObject parseDecision(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String text = raw.trim();
        int start = text.indexOf('{');
        if (start < 0) {
            return null;
        }
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
            } else if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    try {
                        return JSON.parseObject(text.substring(start, i + 1));
                    } catch (Exception e) {
                        log.warn("模型决策 JSON 解析失败 length={}", text.length());
                        return null;
                    }
                }
            }
        }
        return null;
    }

    /** 构造 ToolInvokeDTO：confirmed 恒为 FALSE，模型无权放行。 */
    private ToolInvokeDTO buildInvoke(String toolName, Map<String, Object> params,
                                      String sessionId, String tenantCode, String requestedBy) {
        ToolInvokeDTO invoke = new ToolInvokeDTO();
        invoke.setToolName(toolName);
        invoke.setSessionId(sessionId);
        invoke.setTenantCode(tenantCode);
        invoke.setRequestedBy(requestedBy);
        // 安全底线：模型说已确认也不作数，确认只能走 open 侧 /open/tool/confirm
        invoke.setConfirmed(Boolean.FALSE);
        if (params != null && !params.isEmpty()) {
            String payload = JSON.toJSONString(params);
            invoke.setPayload(payload.length() > MAX_PARAMS_CHARS
                    ? payload.substring(0, MAX_PARAMS_CHARS) : payload);
            String entityId = firstText(params, "entityId", "orderId", "orderNo", "couponNo");
            if (StringUtils.hasText(entityId)) {
                invoke.setEntityId(entityId);
                invoke.setEntityType("order");
            }
        }
        invoke.setIdempotencyKey(ToolInvokeSnapshot.idempotencyKey(tenantCode, invoke));
        return invoke;
    }

    /** 按 keys 顺序取第一个非空字符串，trim 后返回。 */
    private String firstText(Map<String, Object> params, String... keys) {
        if (params == null || params.isEmpty() || keys == null) {
            return null;
        }
        for (String key : keys) {
            Object value = params.get(key);
            if (value == null) {
                continue;
            }
            String text = String.valueOf(value).trim();
            if (StringUtils.hasText(text)) {
                return text;
            }
        }
        return null;
    }

    /** source=confirm-required 或 (confirmId!=null && !success) 视为待确认。 */
    private boolean requiresConfirm(ToolInvokeResultDTO data) {
        return data != null && ("confirm-required".equalsIgnoreCase(data.getSource())
                || (data.getConfirmId() != null && !data.isSuccess()));
    }

    /** 把 ToolInvokeResultDTO 的确认字段 + summary/requestedBy 填进卡片。 */
    private ToolConfirmCardDTO buildCard(ToolInvokeResultDTO data, String summary, String requestedBy) {
        ToolConfirmCardDTO card = new ToolConfirmCardDTO();
        card.setConfirmId(data.getConfirmId());
        card.setToolName(data.getToolName());
        card.setRisk(data.getRisk());
        card.setStatus(StringUtils.hasText(data.getStatus()) ? data.getStatus() : "CONFIRMING");
        card.setExpireAt(data.getExpireAt());
        card.setSummary(summary);
        card.setRequestedBy(requestedBy);
        return card;
    }

    /** 按 SUMMARY_KEYS + SUMMARY_LABELS 拼「标签：值」，中文逗号分隔，超长截断加省略号。 */
    private String summaryOf(Map<String, Object> params) {
        if (params == null || params.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String key : SUMMARY_KEYS) {
            Object value = params.get(key);
            if (value == null) {
                continue;
            }
            String text = String.valueOf(value).trim();
            if (!StringUtils.hasText(text)) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append('，');
            }
            sb.append(SUMMARY_LABELS.getOrDefault(key, key)).append('：').append(text);
            if (sb.length() >= MAX_SUMMARY_CHARS) {
                break;
            }
        }
        String summary = sb.toString();
        return summary.length() > MAX_SUMMARY_CHARS
                ? summary.substring(0, MAX_SUMMARY_CHARS) + "…" : summary;
    }
}
