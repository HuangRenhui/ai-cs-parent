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

/**
 * 告警规则与事件存储（内存实现，重启后规则改动丢失）。
 * 内置 4 条规则：服务不可达、模型连续失败默认启用；工具失败率、WebSocket 断开待接指标后端后生效。
 * 告警事件不落库，每次查询时根据健康探测与模型失败指标实时计算生成。
 */
@Component
public class OpsAlertStore {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 规则表：code → 规则，LinkedHashMap 保证展示顺序固定 */
    private final Map<String, OpsAlertRuleVO> rules = new LinkedHashMap<>();

    /** 模型近5分钟失败次数提供者，由 OpsFacadeService 注入 */
    private volatile Supplier<Long> modelFailCountSupplier;

    public OpsAlertStore() {
        add("service.down", "服务不可达", "健康探测失败", 1, "none");
        add("llm.consecutive.fail", "模型连续失败", "近5分钟模型调用失败次数超阈值", 1, "none");
        add("tool.fail.rate", "工具失败率", "需接指标后端后再生效", 0, "none");
        add("ws.disconnect", "WebSocket 断开", "需接指标后端后再生效", 0, "none");
    }

    /**
     * 注入模型失败次数数据源（由门面服务在启动后回调设置）。
     */
    public void setModelFailCountSupplier(Supplier<Long> supplier) {
        this.modelFailCountSupplier = supplier;
    }

    /**
     * 生成告警快照：全量规则 + 基于当前健康状态实时计算出的触发中事件。
     * synchronized 保证规则被并发修改期间读到一致视图。
     */
    public synchronized OpsAlertBundleVO snapshot(List<OpsHealthItemVO> health) {
        OpsAlertBundleVO bundle = new OpsAlertBundleVO();
        bundle.setRules(new ArrayList<>(rules.values()));
        bundle.setEvents(liveEvents(health));
        bundle.setHint("告警通道（邮件/企微）属 C 阶段。当前根据健康探测与模型失败指标生成临时事件，规则保存在内存。");
        return bundle;
    }

    /**
     * 批量更新规则的启停与通知通道；只允许修改已存在规则，忽略未知编码，防止调用方伪造规则。
     */
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

    /**
     * 实时计算触发中的告警事件：
     * - service.down：任一被探测服务状态为 DOWN 即触发一条；
     * - llm.consecutive.fail：近 5 分钟模型调用失败次数达到阈值（5 次）即触发。
     */
    private List<OpsAlertEventVO> liveEvents(List<OpsHealthItemVO> health) {
        List<OpsAlertEventVO> events = new ArrayList<>();
        String now = LocalDateTime.now().format(FMT);

        // 规则一：服务不可达——逐服务检查健康探测结果
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

        // 规则二：模型连续失败——向基础服务查询近 5 分钟失败次数，超阈值触发
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

    /**
     * 注册一条内置规则。
     */
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
