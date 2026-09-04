package com.ai.cs.ops.service;

import com.ai.cs.ops.dto.OpsAlertBundleVO;
import com.ai.cs.ops.dto.OpsAlertEventVO;
import com.ai.cs.ops.dto.OpsAlertRuleVO;
import com.ai.cs.ops.dto.OpsHealthItemVO;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class OpsAlertStore {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Map<String, OpsAlertRuleVO> rules = new LinkedHashMap<>();

    public OpsAlertStore() {
        add("service.down", "服务不可达", "健康探测失败", 1, "none");
        add("llm.consecutive.fail", "LLM 连续失败", "需接指标后端后再生效", 0, "none");
        add("tool.fail.rate", "工具失败率", "需接指标后端后再生效", 0, "none");
        add("ws.disconnect", "WebSocket 断开", "需接指标后端后再生效", 0, "none");
    }

    public synchronized OpsAlertBundleVO snapshot(List<OpsHealthItemVO> health) {
        OpsAlertBundleVO bundle = new OpsAlertBundleVO();
        bundle.setRules(new ArrayList<>(rules.values()));
        bundle.setEvents(liveEvents(health));
        bundle.setHint("告警通道（邮件/企微）与指标阈值属 C 阶段。当前仅根据健康探测生成临时事件，规则保存在内存。");
        return bundle;
    }

    public synchronized OpsAlertBundleVO replaceRules(List<OpsAlertRuleVO> incoming) {
        if (incoming != null) {
            for (OpsAlertRuleVO rule : incoming) {
                if (rule == null || rule.getCode() == null || !rules.containsKey(rule.getCode())) {
                    continue;
                }
                OpsAlertRuleVO current = rules.get(rule.getCode());
                if (rule.getEnabled() != null) {
                    current.setEnabled(rule.getEnabled());
                }
                if (rule.getChannel() != null) {
                    current.setChannel(rule.getChannel());
                }
            }
        }
        return snapshot(List.of());
    }

    private List<OpsAlertEventVO> liveEvents(List<OpsHealthItemVO> health) {
        List<OpsAlertEventVO> events = new ArrayList<>();
        OpsAlertRuleVO downRule = rules.get("service.down");
        if (downRule == null || !Integer.valueOf(1).equals(downRule.getEnabled()) || health == null) {
            return events;
        }
        String now = LocalDateTime.now().format(FMT);
        for (OpsHealthItemVO item : health) {
            if (!"DOWN".equalsIgnoreCase(item.getStatus())) {
                continue;
            }
            OpsAlertEventVO event = new OpsAlertEventVO();
            event.setRuleCode(downRule.getCode());
            event.setTitle(item.getName() + " 不可达");
            event.setStatus("FIRING");
            event.setMessage(item.getMessage());
            event.setTime(now);
            events.add(event);
        }
        return events;
    }

    private void add(String code, String name, String description, int enabled, String channel) {
        OpsAlertRuleVO rule = new OpsAlertRuleVO();
        rule.setCode(code);
        rule.setName(name);
        rule.setDescription(description);
        rule.setEnabled(enabled);
        rule.setChannel(channel);
        rules.put(code, rule);
    }
}
