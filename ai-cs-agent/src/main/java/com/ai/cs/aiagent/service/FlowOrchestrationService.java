package com.ai.cs.aiagent.service;

import com.ai.cs.common.llm.ModelRouter;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.Map;

/**
 * 流程编排服务（占位）：可视化决策树（自助「点选」+ 大模型混合）
 *
 * <p>TODO 后续实现：决策树 JSON 存于 {@code flow:tree:<flowId>}，当前节点状态存于
 * {@code flow:state:<flowId>:<sessionId>}（TTL 30 分钟）。执行时按节点类型推进：
 * <ul>
 *   <li>{@code option}：用户点选，按节点的 next 映射跳转；无输入时回显选项列表；</li>
 *   <li>{@code llm}：把节点 prompt 拼上用户输入调用模型，按返回文本命中的 next 键跳转；</li>
 *   <li>{@code action}：透出动作名与会话 ID 供上层执行工具；</li>
 *   <li>{@code end}：清除流程状态并结束。</li>
 * </ul>
 * 节点缺失、类型未知等异常情况统一返回 {@code FlowStepResult.error}。</p>
 *
 * <p>当前不读写 Redis、不调用模型：{@link #loadFlowTree} 返回 null、{@link #saveFlowTree} 不落库、
 * {@link #executeStep} 统一返回 error 结果。</p>
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

    /**
     * 加载决策树配置（占位：恒返回 null）
     *
     * @param flowId 决策树ID
     * @return null
     */
    public JSONObject loadFlowTree(String flowId) {
        log.info("[占位] 决策树加载未实现 flowId={}，返回 null", flowId);
        return null;
    }

    /**
     * 保存决策树配置（占位：不落库）
     */
    public void saveFlowTree(String flowId, JSONObject tree) {
        log.info("[占位] 决策树保存未实现 flowId={}", flowId);
    }

    /**
     * 执行决策树（占位：不推进任何节点）
     *
     * @param flowId    决策树ID
     * @param sessionId 会话ID
     * @param userMsg   用户消息（点选值或自然语言）
     * @return error 结果
     */
    public FlowStepResult executeStep(String flowId, String sessionId, String userMsg) {
        log.warn("[占位] 决策树执行未实现 flowId={} sessionId={}", flowId, sessionId);
        return FlowStepResult.error("流程编排为占位实现，后端未接入决策树执行");
    }

    /**
     * 决策树步骤执行结果
     */
    public static class FlowStepResult {
        private String type;
        private String text;
        private com.alibaba.fastjson2.JSONArray options;
        private Map<String, Object> data;

        public String getType() { return type; }
        public String getText() { return text; }
        public com.alibaba.fastjson2.JSONArray getOptions() { return options; }
        public Map<String, Object> getData() { return data; }

        static FlowStepResult options(String text, com.alibaba.fastjson2.JSONArray options) {
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
