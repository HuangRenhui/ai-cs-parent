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
import java.util.function.Supplier;

@Component
public class OpsAlertStore {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Map<String, OpsAlertRuleVO> rules = new LinkedHashMap<>();

    /** 模型近5分钟失败次数提供者，由 OpsFacadeService 注入 */
    private volatile Supplier<Long> modelFailCountSupplier;

    public OpsAlertStore() {
        add("service.down", "服务不可达", "健康探测失败", 1, "none");
        add("llm.consecutive.fail", "模型连续失败", "近5分钟模型调用失败次数超阈值", 1, "none");
        add("tool.fail.rate", "工具失败率", "需接指标后端后再生效", 0, "none");
        add("ws.disconnect", "WebSocket 断开", "需接指标后端后再生效", 0, "none");
    }

    public void setModelFailCountSupplier(Supplier<Long> supplier) {
        this.modelFailCountSupplier = supplier;
    }

    public synchronized OpsAlertBundleVO snapshot(List<OpsHealthItemVO> health) {
        OpsAlertBundleVO bundle = new OpsAlertBundleVO();
        bundle.setRules(new ArrayList<>(rules.values()));
        bundle.setEvents(liveEvents(health));
        bundle.setHint("告警通道（邮件/企微）属 C 阶段。当前根据健康探测与模型失败指标生成临时事件，规则保存在内存。");
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
        String now = LocalDateTime.now().format(FMT);

        OpsAlertRuleVO downRule = rules.get("service.down");
        if (downRule != null && Integer.valueOf(1).equals(downRule.getEnabled()) && health != null) {
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
        }

        OpsAlertRuleVO llmRule = rules.get("llm.consecutive.fail");
        if (llmRule != null && Integer.valueOf(1).equals(llmRule.getEnabled()) && modelFailCountSupplier != null) {
            Long fails = modelFailCountSupplier.get();
            if (fails != null && fails >= 5) {
                OpsAlertEventVO event = new OpsAlertEventVO();
                event.setRuleCode(llmRule.getCode());
                event.setTitle("模型调用连续失败");
                event.setStatus("FIRING");
                event.setMessage("近5分钟模型调用失败 " + fails + " 次，请检查模型可用性与密钥");
                event.setTime(now);
                events.add(event);
            }
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
