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

    @GetMapping("/overview")
    public Result<Map<String, Object>> overview() {
        return Result.success(statisticsService.getOverview());
    }

    @GetMapping("/dashboard")
    public Result<Map<String, Object>> dashboard() {
        return Result.success(statisticsService.getDashboardData());
    }

    @PostMapping("/chat-trend")
    public Result<List<Map<String, Object>>> chatTrend(@RequestBody StatisticsDTO dto) {
        return Result.success(statisticsService.getChatTrend(
                dto.getStartDate(), dto.getEndDate()));
    }

    @GetMapping("/workorder-stats")
    public Result<Map<String, Object>> workOrderStats() {
        return Result.success(statisticsService.getWorkOrderStats());
    }

    @PostMapping("/customer-trend")
    public Result<List<Map<String, Object>>> customerTrend(@RequestBody StatisticsDTO dto) {
        return Result.success(statisticsService.getCustomerTrend(
                dto.getStartDate(), dto.getEndDate()));
    }
}
