package com.ai.cs.aiagent.service;

import com.ai.cs.common.llm.ModelCallException;
import com.ai.cs.common.llm.ModelRouter;
import com.ai.cs.common.llm.ModelTypeEnum;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import jakarta.annotation.Resource;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 流程编排服务：可视化决策树（自助「点选」+ 大模型混合）
 * 补全功能清单 P1 缺失「流程编排」
 * 决策树节点类型：option（用户点选）、llm（模型判断）、action（执行工具）、end（结束）
 *
 * @author huangrenhui
 */
@Slf4j
@Service
public class FlowOrchestrationService {

    @Resource
    private ModelRouter modelRouter;

    @Resource
    private RedisTemplate<String, String> redisTemplate;

    private static final String FLOW_KEY_PREFIX = "flow:tree:";
    private static final String FLOW_STATE_PREFIX = "flow:state:";
    private static final long FLOW_STATE_EXPIRE_MINUTES = 30;

    /**
     * 加载决策树配置
     *
     * @param flowId 决策树ID
     * @return 决策树JSON配置
     */
    public JSONObject loadFlowTree(String flowId) {
        if (redisTemplate == null) {
            return null;
        }
        try {
            String key = FLOW_KEY_PREFIX + flowId;
            String json = redisTemplate.opsForValue().get(key);
            if (json != null) {
                return JSON.parseObject(json);
            }
        } catch (Exception e) {
            log.warn("加载决策树失败 flowId={}: {}", flowId, e.getMessage());
        }
        return null;
    }

    /**
     * 保存决策树配置
     */
    public void saveFlowTree(String flowId, JSONObject tree) {
        if (redisTemplate == null) {
            return;
        }
        try {
            String key = FLOW_KEY_PREFIX + flowId;
            redisTemplate.opsForValue().set(key, tree.toJSONString());
        } catch (Exception e) {
            log.warn("保存决策树失败 flowId={}: {}", flowId, e.getMessage());
        }
    }

    /**
     * 执行决策树：从当前节点开始，按类型推进
     *
     * @param flowId    决策树ID
     * @param sessionId 会话ID
     * @param userMsg   用户消息（点选值或自然语言）
     * @return 当前节点的输出（选项列表/执行结果/结束文案）
     */
    public FlowStepResult executeStep(String flowId, String sessionId, String userMsg) {
        JSONObject tree = loadFlowTree(flowId);
        if (tree == null) {
            return FlowStepResult.error("决策树不存在: " + flowId);
        }

        String currentNodeId = getCurrentNodeId(flowId, sessionId);
        if (currentNodeId == null) {
            currentNodeId = tree.getString("startNode");
        }
        if (currentNodeId == null) {
            return FlowStepResult.error("决策树缺少起始节点");
        }

        JSONObject nodes = tree.getJSONObject("nodes");
        if (nodes == null || !nodes.containsKey(currentNodeId)) {
            return FlowStepResult.error("节点不存在: " + currentNodeId);
        }

        JSONObject node = nodes.getJSONObject(currentNodeId);
        String nodeType = node.getString("type");

        return switch (nodeType) {
            case "option" -> handleOptionNode(node, currentNodeId, userMsg, flowId, sessionId, nodes);
            case "llm" -> handleLlmNode(node, currentNodeId, userMsg, flowId, sessionId, nodes);
            case "action" -> handleActionNode(node, currentNodeId, flowId, sessionId, nodes);
            case "end" -> handleEndNode(node, flowId, sessionId);
            default -> FlowStepResult.error("未知节点类型: " + nodeType);
        };
    }

    private FlowStepResult handleOptionNode(JSONObject node, String nodeId, String userMsg,
                                             String flowId, String sessionId, JSONObject nodes) {
        if (!StringUtils.hasText(userMsg)) {
            saveCurrentNodeId(flowId, sessionId, nodeId);
            return FlowStepResult.options(node.getString("text"), node.getJSONArray("options"));
        }
        JSONObject nextMapping = node.getJSONObject("next");
        if (nextMapping != null && nextMapping.containsKey(userMsg)) {
            String nextNodeId = nextMapping.getString(userMsg);
            saveCurrentNodeId(flowId, sessionId, nextNodeId);
            return executeStep(flowId, sessionId, null);
        }
        saveCurrentNodeId(flowId, sessionId, nodeId);
        return FlowStepResult.options(node.getString("text"), node.getJSONArray("options"));
    }

    private FlowStepResult handleLlmNode(JSONObject node, String nodeId, String userMsg,
                                           String flowId, String sessionId, JSONObject nodes) {
        String prompt = node.getString("prompt");
        if (prompt == null) {
            return FlowStepResult.error("LLM节点缺少prompt配置");
        }
        try {
            String response = modelRouter.chatForType(ModelTypeEnum.LLM.getCode(),
                    java.util.List.of(Map.of("role", "user", "content", prompt + "\n用户输入：" + userMsg)));
            JSONObject nextMapping = node.getJSONObject("next");
            if (nextMapping != null) {
                for (Map.Entry<String, Object> entry : nextMapping.entrySet()) {
                    if (response.contains(entry.getKey())) {
                        String nextNodeId = (String) entry.getValue();
                        saveCurrentNodeId(flowId, sessionId, nextNodeId);
                        return executeStep(flowId, sessionId, null);
                    }
                }
            }
            saveCurrentNodeId(flowId, sessionId, nodeId);
            return FlowStepResult.text(response);
        } catch (ModelCallException e) {
            log.error("LLM节点执行失败", e);
            return FlowStepResult.error("模型调用失败，请稍后重试");
        }
    }

    private FlowStepResult handleActionNode(JSONObject node, String nodeId,
                                             String flowId, String sessionId, JSONObject nodes) {
        String actionName = node.getString("action");
        String text = node.getString("text");
        JSONObject nextMapping = node.getJSONObject("next");
        if (nextMapping != null && nextMapping.containsKey("default")) {
            String nextNodeId = nextMapping.getString("default");
            saveCurrentNodeId(flowId, sessionId, nextNodeId);
        }
        Map<String, Object> data = new HashMap<>();
        data.put("action", actionName);
        data.put("sessionId", sessionId);
        return FlowStepResult.action(text, data);
    }

    private FlowStepResult handleEndNode(JSONObject node, String flowId, String sessionId) {
        clearFlowState(flowId, sessionId);
        return FlowStepResult.end(node.getString("text"));
    }

    private String getCurrentNodeId(String flowId, String sessionId) {
        if (redisTemplate == null) {
            return null;
        }
        try {
            return redisTemplate.opsForValue().get(FLOW_STATE_PREFIX + flowId + ":" + sessionId);
        } catch (Exception e) {
            return null;
        }
    }

    private void saveCurrentNodeId(String flowId, String sessionId, String nodeId) {
        if (redisTemplate == null) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(FLOW_STATE_PREFIX + flowId + ":" + sessionId, nodeId,
                    FLOW_STATE_EXPIRE_MINUTES, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("保存流程状态失败: {}", e.getMessage());
        }
    }

    private void clearFlowState(String flowId, String sessionId) {
        if (redisTemplate == null) {
            return;
        }
        try {
            redisTemplate.delete(FLOW_STATE_PREFIX + flowId + ":" + sessionId);
        } catch (Exception e) {
            log.warn("清除流程状态失败: {}", e.getMessage());
        }
    }

    /**
     * 决策树步骤执行结果
     */
    public static class FlowStepResult {
        private String type;
        private String text;
        private com.alibaba.fastjson.JSONArray options;
        private Map<String, Object> data;

        public String getType() { return type; }
        public String getText() { return text; }
        public com.alibaba.fastjson.JSONArray getOptions() { return options; }
        public Map<String, Object> getData() { return data; }

        static FlowStepResult options(String text, com.alibaba.fastjson.JSONArray options) {
            FlowStepResult r = new FlowStepResult();
            r.type = "options";
            r.text = text;
            r.options = options;
            return r;
        }

        static FlowStepResult text(String text) {
            FlowStepResult r = new FlowStepResult();
            r.type = "text";
            r.text = text;
            return r;
        }

        static FlowStepResult action(String text, Map<String, Object> data) {
            FlowStepResult r = new FlowStepResult();
            r.type = "action";
            r.text = text;
            r.data = data;
            return r;
        }

        static FlowStepResult end(String text) {
            FlowStepResult r = new FlowStepResult();
            r.type = "end";
            r.text = text;
            return r;
        }

        static FlowStepResult error(String text) {
            FlowStepResult r = new FlowStepResult();
            r.type = "error";
            r.text = text;
            return r;
        }
    }
}