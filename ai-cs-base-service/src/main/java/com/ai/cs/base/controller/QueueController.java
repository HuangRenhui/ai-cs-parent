package com.ai.cs.base.controller;

import com.ai.cs.common.result.Result;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 排队与转接控制器
 *
 * <p>演示桩：返回内置假数据，尚未接入真实路由与排队引擎。</p>
 *
 * @author huangrenhui
 */
@RestController
@RequestMapping("/queue")
public class QueueController {

    /** 演示数据：排队中的会话 */
    private static final List<Map<String, Object>> DEMO = new ArrayList<>();

    static {
        DEMO.add(row("sess_demo_2001", "测试用户1", "退款未到账", "售后组", 42, 1, "2026-09-11 10:01:00"));
        DEMO.add(row("sess_demo_2002", "小王", "物流到哪了", "售后组", 96, 2, "2026-09-11 10:00:10"));
        DEMO.add(row("sess_demo_2003", "物流咨询客", "要开发票", "售前咨询组", 15, 1, "2026-09-11 10:02:30"));
    }

    private static Map<String, Object> row(String sessionId, String customerName, String lastMessage,
                                           String skillGroup, int waitSeconds, int priority, String enqueueTime) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("sessionId", sessionId);
        m.put("customerName", customerName);
        m.put("lastMessage", lastMessage);
        m.put("skillGroup", skillGroup);
        m.put("waitSeconds", waitSeconds);
        m.put("priority", priority);
        m.put("enqueueTime", enqueueTime);
        return m;
    }

    /** 排队列表 */
    @GetMapping("/list")
    public Result<List<Map<String, Object>>> list() {
        return Result.success(DEMO);
    }

    /** 排队监控指标 */
    @GetMapping("/monitor")
    public Result<Map<String, Object>> monitor() {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("waiting", DEMO.size());
        dto.put("avgWaitSeconds", 51);
        dto.put("maxWaitSeconds", 96);
        dto.put("onlineAgents", 6);
        dto.put("busyAgents", 4);
        dto.put("idleAgents", 2);
        dto.put("assignToday", 37);
        dto.put("abandonToday", 2);
        return Result.success(dto);
    }

    /** 手动分配坐席（演示桩） */
    @PutMapping("/{sessionId}/assign")
    public Result<String> assign(@PathVariable String sessionId, @RequestParam Long agentId) {
        return Result.success("已分配坐席（演示）");
    }

    /** 转接会话（演示桩） */
    @PutMapping("/{sessionId}/transfer")
    public Result<String> transfer(@PathVariable String sessionId,
                                   @RequestParam(required = false) String skillGroup,
                                   @RequestParam(required = false) Long agentId) {
        return Result.success("已转接（演示）");
    }

    /** 发起咨询（演示桩） */
    @PutMapping("/{sessionId}/consult")
    public Result<String> consult(@PathVariable String sessionId, @RequestParam Long agentId) {
        return Result.success("已发起咨询（演示）");
    }

    /** 移除排队（演示桩） */
    @DeleteMapping("/{sessionId}")
    public Result<String> remove(@PathVariable String sessionId) {
        return Result.success("已移出排队（演示）");
    }
}
