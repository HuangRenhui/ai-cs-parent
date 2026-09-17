package com.ai.cs.base.controller;

import com.ai.cs.base.service.StatisticsService;
import com.ai.cs.common.dto.StatisticsDTO;
import com.ai.cs.common.result.Result;
import org.springframework.web.bind.annotation.*;
import jakarta.annotation.Resource;

import java.util.List;
import java.util.Map;

/**
 * 数据统计控制器
 *
 * @author huangrenhui
 * @date 2026/7/20
 */
@RestController
@RequestMapping("/statistics")
public class StatisticsController {

    @Resource
    private StatisticsService statisticsService;

    /**
     * 系统概览（今日/本周 会话、工单、客户数）
     */
    @GetMapping("/overview")
    public Result<Map<String, Object>> overview() {
        return Result.success(statisticsService.getOverview());
    }

    /**
     * 仪表盘汇总（概览 + 近30天趋势 + 工单统计）
     */
    @GetMapping("/dashboard")
    public Result<Map<String, Object>> dashboard() {
        return Result.success(statisticsService.getDashboardData());
    }

    /**
     * 聊天会话趋势（按天）
     */
    @PostMapping("/chat-trend")
    public Result<List<Map<String, Object>>> chatTrend(@RequestBody StatisticsDTO dto) {
        return Result.success(statisticsService.getChatTrend(
                dto.getStartDate(), dto.getEndDate()));
    }

    /**
     * 工单统计（状态分布、类型分布、坐席排行）
     */
    @GetMapping("/workorder-stats")
    public Result<Map<String, Object>> workOrderStats() {
        return Result.success(statisticsService.getWorkOrderStats());
    }

    /**
     * 客户新增趋势（按天）
     */
    @PostMapping("/customer-trend")
    public Result<List<Map<String, Object>>> customerTrend(@RequestBody StatisticsDTO dto) {
        return Result.success(statisticsService.getCustomerTrend(
                dto.getStartDate(), dto.getEndDate()));
    }
}
