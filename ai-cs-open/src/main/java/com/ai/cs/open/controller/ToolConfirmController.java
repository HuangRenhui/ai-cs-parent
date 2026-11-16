package com.ai.cs.open.controller;

import com.ai.cs.common.dto.ToolCancelDTO;
import com.ai.cs.common.dto.ToolConfirmDTO;
import com.ai.cs.common.dto.ToolInvokeResultDTO;
import com.ai.cs.common.dto.ToolPendingDTO;
import com.ai.cs.common.dto.ToolRejectDTO;
import com.ai.cs.common.result.PageResult;
import com.ai.cs.common.result.Result;
import com.ai.cs.common.security.RequirePermission;
import com.ai.cs.open.service.ToolConfirmService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 高危工具人工确认控制器。
 *
 * <p>与 {@link OpenToolController#invoke} 的关系：invoke 命中 {@code confirm-required}
 * 时返回 confirmId，坐席台用本控制器的 pending/confirm/reject/cancel 完成「人这一环」。</p>
 *
 * <p><b>权限</b>：除取消外均要求 {@code tool:confirm}；未配置该权限点的角色会 403，
 * 需随「岗位角色 RBAC 种子数据」（后端未完成清单 §8）一起补齐。取消只校验登录态，
 * 由服务层判断「发起方本人或有确认权限」。</p>
 */
@RestController
@RequestMapping("/open/tool")
public class ToolConfirmController {

    @Resource
    private ToolConfirmService toolConfirmService;

    /**
     * 待确认分页：按「快超时的在前」排序，不过期（expire_at 为空）的排最后。
     *
     * @param tenantCode 仅平台级账号需要传；令牌绑定租户时以令牌为准
     */
    @RequirePermission("tool:confirm")
    @GetMapping("/pending")
    public Result<PageResult<ToolPendingDTO>> pending(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String sessionId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String tenantCode) {
        return Result.success(toolConfirmService.pagePending(pageNum, pageSize, tenantCode, sessionId, status));
    }

    /**
     * 待确认详情：含完整 requestJson，供确认人核对金额/券号/订单号。
     */
    @RequirePermission("tool:confirm")
    @GetMapping("/pending/{id}")
    public Result<ToolPendingDTO> detail(@PathVariable Long id,
                                         @RequestParam(required = false) String tenantCode) {
        return Result.success(toolConfirmService.detail(id, tenantCode));
    }

    /**
     * 确认并执行。调用方必须是登录用户；确认人只从 JWT 取，请求体无法指定。
     */
    @RequirePermission("tool:confirm")
    @PostMapping("/confirm")
    public Result<ToolInvokeResultDTO> confirm(@RequestBody ToolConfirmDTO dto) {
        return Result.success(toolConfirmService.confirm(dto));
    }

    /**
     * 拒绝：终态 REJECTED，用户可重新发起。
     */
    @RequirePermission("tool:confirm")
    @PostMapping("/reject")
    public Result<Void> reject(@RequestBody ToolRejectDTO dto) {
        toolConfirmService.reject(dto);
        return Result.success();
    }

    /**
     * 取消：发起方本人或有确认权限的坐席。
     */
    @PostMapping("/cancel")
    public Result<Void> cancel(@RequestBody ToolCancelDTO dto) {
        toolConfirmService.cancel(dto);
        return Result.success();
    }
}
