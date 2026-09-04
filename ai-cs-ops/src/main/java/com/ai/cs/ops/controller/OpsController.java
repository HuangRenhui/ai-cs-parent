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

@RestController
@RequestMapping("/ops")
public class OpsController {

    @Resource
    private OpsFacadeService opsFacadeService;

    @GetMapping("/overview")
    public Result<OpsOverviewVO> overview() {
        return Result.success(opsFacadeService.overview());
    }

    @GetMapping("/health")
    public Result<List<OpsHealthItemVO>> health() {
        return Result.success(opsFacadeService.health());
    }

    @GetMapping("/health/self")
    public Result<Map<String, String>> self() {
        return Result.success(opsFacadeService.self());
    }

    @GetMapping("/logs")
    public Result<OpsLogPageVO> logs(OpsLogQuery query) {
        return Result.success(opsFacadeService.logs(query));
    }

    @GetMapping("/logs/{requestId}")
    public Result<List<OpsLogEntryVO>> logsByRequestId(@PathVariable String requestId) {
        return Result.success(opsFacadeService.logsByRequestId(requestId));
    }

    @GetMapping("/traces")
    public Result<OpsTracePageVO> traces(@RequestParam(required = false) String traceId,
                                         @RequestParam(required = false) String sessionId,
                                         @RequestParam(required = false) Integer page,
                                         @RequestParam(required = false) Integer size) {
        return Result.success(opsFacadeService.traces(traceId, sessionId, page, size));
    }

    @GetMapping("/traces/{traceId}")
    public Result<OpsTraceVO> traceDetail(@PathVariable String traceId) {
        return Result.success(opsFacadeService.traceDetail(traceId));
    }

    @GetMapping("/alerts")
    public Result<OpsAlertBundleVO> alerts() {
        return Result.success(opsFacadeService.alerts());
    }

    @PutMapping("/alerts")
    public Result<OpsAlertBundleVO> saveAlerts(@RequestBody(required = false) List<OpsAlertRuleVO> rules) {
        return Result.success(opsFacadeService.saveAlerts(rules));
    }
}
