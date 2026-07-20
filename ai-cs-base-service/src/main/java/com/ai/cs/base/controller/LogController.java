package com.ai.cs.base.controller;

import com.ai.cs.base.entity.OperationLog;
import com.ai.cs.base.service.OperationLogService;
import com.ai.cs.common.result.Result;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.web.bind.annotation.*;
import jakarta.annotation.Resource;

/**
 * 操作日志控制器
 *
 * @author huangrenhui
 * @date 2026/7/20
 */
@RestController
@RequestMapping("/system/log")
public class LogController {

    @Resource
    private OperationLogService logService;

    @GetMapping("/page")
    public Result<Page<OperationLog>> page(@RequestParam(defaultValue = "1") int pageNum,
                                            @RequestParam(defaultValue = "10") int pageSize,
                                            @RequestParam(required = false) Long userId,
                                            @RequestParam(required = false) String module) {
        return Result.success(logService.queryPage(pageNum, pageSize, userId, module));
    }
}
