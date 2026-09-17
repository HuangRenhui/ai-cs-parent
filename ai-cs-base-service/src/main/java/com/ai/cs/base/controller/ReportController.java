package com.ai.cs.base.controller;

import com.ai.cs.common.result.Result;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 报表中心控制器（满意度 / 时效 / 概览）
 *
 * <p>演示桩：返回内置假数据，尚未接入真实统计。</p>
 *
 * @author huangrenhui
 */
@RestController
@RequestMapping("/report")
public class ReportController {

    /** 报表概览 */
    @GetMapping("/overview")
    public Result<Map<String, Object>> overview() {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("inbound", 1280);
        dto.put("aiHandled", 942);
        dto.put("transferHuman", 338);
        dto.put("aiDeflectRate", 73.6);
        dto.put("avgFirstReplySeconds", 6);
        dto.put("avgHandleSeconds", 214);
        dto.put("satisfactionRate", 96.2);
        return Result.success(dto);
    }

    /** CSAT / NPS 满意度 */
    @GetMapping("/csat")
    public Result<Map<String, Object>> csat() {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("csat", 4.72);
        dto.put("nps", 42);
        dto.put("evaluated", 386);
        dto.put("satisfied", 371);
        dto.put("dissatisfied", 15);
        dto.put("distribution", List.of(
                item("非常满意", 268),
                item("满意", 103),
                item("一般", 12),
                item("不满意", 3)
        ));
        return Result.success(dto);
    }

    /** 时效报表（FRT / AHT / SLA） */
    @GetMapping("/sla")
    public Result<Map<String, Object>> sla() {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("frtSeconds", 6);
        dto.put("ahtSeconds", 214);
        dto.put("slaTargetSeconds", 30);
        dto.put("slaHitRate", 98.4);
        dto.put("rows", List.of(
                slaRow("售后组", 4, 226, 99.1),
                slaRow("售前咨询组", 7, 198, 97.6),
                slaRow("金融专席", 9, 241, 95.8)
        ));
        return Result.success(dto);
    }

    private static Map<String, Object> item(String name, long value) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("value", value);
        return m;
    }

    private static Map<String, Object> slaRow(String group, int frt, int aht, double hitRate) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("group", group);
        m.put("frtSeconds", frt);
        m.put("ahtSeconds", aht);
        m.put("slaHitRate", hitRate);
        return m;
    }

    /** 进线量趋势（近 7 天） */
    @GetMapping("/trend")
    public Result<List<Map<String, Object>>> trend() {
        List<Map<String, Object>> rows = new ArrayList<>();
        String[] labels = { "09-05", "09-06", "09-07", "09-08", "09-09", "09-10", "09-11" };
        int[] values = { 168, 192, 155, 210, 244, 198, 231 };
        for (int i = 0; i < labels.length; i++) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("date", labels[i]);
            m.put("count", values[i]);
            rows.add(m);
        }
        return Result.success(rows);
    }
}
