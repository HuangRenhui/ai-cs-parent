package com.ai.cs.open.controller;

import com.ai.cs.common.result.Result;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 极简接入向导控制器
 *
 * <p>演示桩：返回内置步骤与进度，尚未接入真实开通流程。</p>
 *
 * @author huangrenhui
 */
@RestController
@RequestMapping("/onboarding")
public class OnboardingController {

    /** 向导步骤 */
    @GetMapping("/steps")
    public Result<List<Map<String, Object>>> steps() {
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(step(1, "开通租户", "创建租户并选择套餐", "已完成", "/system/tenant"));
        rows.add(step(2, "选择行业包", "电商 / 金融 / 新零售", "已完成", "/open"));
        rows.add(step(3, "配置连接器", "对接订单与物流接口", "进行中", "/open"));
        rows.add(step(4, "接入 SDK", "网页 / 小程序 / APP", "未开始", "/open/widget-config"));
        rows.add(step(5, "导入知识", "FAQ 与文档", "未开始", "/knowledge"));
        rows.add(step(6, "配置回调", "会话进展与工单事件", "未开始", "/open/webhook"));
        return Result.success(rows);
    }

    /** 向导进度 */
    @GetMapping("/progress")
    public Result<Map<String, Object>> progress() {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("total", 6);
        dto.put("done", 2);
        dto.put("current", 3);
        dto.put("percent", 33);
        return Result.success(dto);
    }

    private static Map<String, Object> step(int order, String title, String desc, String status, String link) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("order", order);
        m.put("title", title);
        m.put("desc", desc);
        m.put("status", status);
        m.put("link", link);
        return m;
    }
}
