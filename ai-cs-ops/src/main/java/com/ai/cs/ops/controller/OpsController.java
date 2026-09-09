package com.ai.cs.ops.controller;

import com.ai.cs.common.result.Result;
import com.ai.cs.ops.dto.OpsAlertBundleVO;
import com.ai.cs.ops.dto.OpsAlertRuleVO;
import com.ai.cs.ops.dto.OpsHealthItemVO;
import com.ai.cs.ops.dto.OpsLogEntryVO;
import com.ai.cs.ops.dto.OpsLogPageVO;
import com.ai.cs.ops.dto.OpsLogQuery;
import com.ai.cs.ops.dto.OpsOverviewVO;
import com.ai.cs.ops.dto.OpsTracePageVO;
import com.ai.cs.ops.dto.OpsTraceVO;
import com.ai.cs.ops.service.OpsFacadeService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 运营中心控制器：健康探测、日志检索、链路追踪、告警规则的统一查询/配置接口。
 */
@RestController
@RequestMapping("/ops")
public class OpsController {

    @Resource
    private OpsFacadeService opsFacadeService;

    /**
     * 运营总览：服务健康、日志错误数、待处理告警的一屏聚合数据。
     */
    @GetMapping("/overview")
    public Result<OpsOverviewVO> overview() {
        return Result.success(opsFacadeService.overview());
    }

    /**
     * 实时探测全部已配置服务的健康状态。
     */
    @GetMapping("/health")
    public Result<List<OpsHealthItemVO>> health() {
        return Result.success(opsFacadeService.health());
    }

    /**
     * 本服务自检：供其他服务探测本服务是否存活。
     */
    @GetMapping("/health/self")
    public Result<Map<String, String>> self() {
        return Result.success(opsFacadeService.self());
    }

    /**
     * 多条件分页检索日志（条件从 query string 绑定到 OpsLogQuery）。
     */
    @GetMapping("/logs")
    public Result<OpsLogPageVO> logs(OpsLogQuery query) {
        return Result.success(opsFacadeService.logs(query));
    }

    /**
     * 按请求 ID 串查全链路日志（时间正序）。
     */
    @GetMapping("/logs/{requestId}")
    public Result<List<OpsLogEntryVO>> logsByRequestId(@PathVariable String requestId) {
        return Result.success(opsFacadeService.logsByRequestId(requestId));
    }

    /**
     * 分页查询链路：可按 traceId / sessionId 过滤。
     */
    @GetMapping("/traces")
    public Result<OpsTracePageVO> traces(@RequestParam(required = false) String traceId,
                                         @RequestParam(required = false) String sessionId,
                                         @RequestParam(required = false) Integer page,
                                         @RequestParam(required = false) Integer size) {
        return Result.success(opsFacadeService.traces(traceId, sessionId, page, size));
    }

    /**
     * 查询单条链路详情（各服务 Span 的起止时间）。
     */
    @GetMapping("/traces/{traceId}")
    public Result<OpsTraceVO> traceDetail(@PathVariable String traceId) {
        return Result.success(opsFacadeService.traceDetail(traceId));
    }

    /**
     * 查询告警快照：全部规则 + 当前触发中的事件。
     */
    @GetMapping("/alerts")
    public Result<OpsAlertBundleVO> alerts() {
        return Result.success(opsFacadeService.alerts());
    }

    /**
     * 保存告警规则（启停与通知通道），返回最新快照。
     */
    @PutMapping("/alerts")
    public Result<OpsAlertBundleVO> saveAlerts(@RequestBody(required = false) List<OpsAlertRuleVO> rules) {
        return Result.success(opsFacadeService.saveAlerts(rules));
    }
}
