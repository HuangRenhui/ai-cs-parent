package com.ai.cs.base.controller;

import com.ai.cs.base.entity.OperationLog;
import com.ai.cs.base.service.OperationLogService;
import com.ai.cs.common.dto.AccessAnalysisDTO;
import com.ai.cs.common.result.Result;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

/**
 * 操作审计接口：分页与流量分析对后台管理员开放；删除仅超级管理员可调用。
 *
 * @author huangrenhui
 * @date 2026/7/20
 */
@RestController
@RequestMapping("/system/log")
public class LogController {

    @Resource
    private OperationLogService logService;

    /**
     * 分页查询操作日志（可按用户、账号、模块、来源 IP、时间过滤）。
     * 非超管即使传入更早的 beginTime，服务端也会裁剪到近一个月。
     */
    @GetMapping("/page")
    public Result<Page<OperationLog>> page(@RequestParam(defaultValue = "1") int pageNum,
                                            @RequestParam(defaultValue = "10") int pageSize,
                                            @RequestParam(required = false) Long userId,
                                            @RequestParam(required = false) String module,
                                            @RequestParam(required = false) String username,
                                            @RequestParam(required = false) String ip,
                                            @RequestParam(required = false)
                                            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime beginTime,
                                            @RequestParam(required = false)
                                            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        return Result.success(logService.queryPage(pageNum, pageSize, userId, module, username, ip, beginTime, endTime));
    }

    /**
     * 流量与 IP 分析：按小时看请求量，按来源地址和模块看分布。时间窗口规则与分页查询相同。
     */
    @GetMapping("/analysis")
    public Result<AccessAnalysisDTO> analysis(@RequestParam(required = false)
                                              @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime beginTime,
                                              @RequestParam(required = false)
                                              @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        return Result.success(logService.analyze(beginTime, endTime));
    }

    /**
     * 删除一条操作审计。仅最高超级管理员可删，其他人返回 403。
     */
    @DeleteMapping("/delete/{id}")
    public Result<String> delete(@PathVariable Long id) {
        logService.deleteById(id);
        return Result.success("删除成功");
    }
}
