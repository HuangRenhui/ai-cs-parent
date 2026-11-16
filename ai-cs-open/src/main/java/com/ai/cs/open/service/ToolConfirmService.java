package com.ai.cs.open.service;

import com.ai.cs.common.dto.ToolCancelDTO;
import com.ai.cs.common.dto.ToolConfirmDTO;
import com.ai.cs.common.dto.ToolInvokeDTO;
import com.ai.cs.common.dto.ToolInvokeResultDTO;
import com.ai.cs.common.dto.ToolPendingDTO;
import com.ai.cs.common.dto.ToolRejectDTO;
import com.ai.cs.common.exception.BusinessException;
import com.ai.cs.common.result.PageResult;
import com.ai.cs.common.security.JwtContext;
import com.ai.cs.open.entity.OpenToolInvoke;
import com.ai.cs.open.enums.ToolInvokeStatus;
import com.ai.cs.open.mapper.OpenToolInvokeMapper;
import com.ai.cs.open.util.ToolInvokeSnapshot;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 高危工具人工确认闭环（Human-in-the-Loop）。
 *
 * <p>职责边界：</p>
 * <ul>
 *   <li><b>本类</b>：待确认记录的查询、状态机流转（确认/拒绝/取消/过期）、租户与操作人校验；</li>
 *   <li><b>{@link OpenPlatformService}</b>：真正出站执行（复用 MOCK/REST、鉴权头、SSRF 校验）。</li>
 * </ul>
 *
 * <p><b>为什么这里不标 {@code @Transactional}</b>：确认链路是「乐观锁抢占 → 出站 HTTP → 回写结果」
 * 三步，若整体一个事务，执行失败后回滚会把「FAILED 留痕」和「confirmed_by/at」一起抹掉，
 * 审计上就查不到「谁确认了但执行失败」。因此每步单独提交，失败只改状态、不删记录。</p>
 */
@Slf4j
@Service
public class ToolConfirmService {

    /** 处理待确认所需的权限点；与 Controller 上的 @RequirePermission 保持一致 */
    private static final String PERMISSION_CONFIRM = "tool:confirm";
    /** 与 DDL 中 idempotency_key VARCHAR(128) 对齐，超长直接拒绝而非让数据库报错 */
    private static final int IDEMPOTENCY_KEY_MAX_LENGTH = 128;
    /** 与 DDL 中 rejected_reason VARCHAR(255) 对齐，超长截断保全审计 */
    private static final int REASON_MAX_LENGTH = 255;
    /** 单页上限，防止前端传超大 pageSize 拖垮查询 */
    private static final int MAX_PAGE_SIZE = 100;

    @Resource
    private OpenToolInvokeMapper invokeMapper;
    @Resource
    private OpenPlatformService openPlatformService;
    @Resource
    private ConnectorInvokeRecorder recorder;

    // ==================== 查询 ====================

    /**
     * 待确认分页。
     *
     * <p>排序：不过期的排在后面、快超时的在前，保证最紧急的待办在首屏。
     * MySQL 的 {@code ORDER BY expire_at ASC} 会把 NULL 排在最前，故显式用
     * {@code (expire_at IS NULL)} 做第一排序键。</p>
     *
     * @param tenantCode 请求参数里的租户（平台级账号用；令牌带租户时以令牌为准）
     */
    public PageResult<ToolPendingDTO> pagePending(int pageNum, int pageSize, String tenantCode,
                                                  String sessionId, String status) {
        String effectiveTenant = JwtContext.resolveTenantCode(tenantCode);
        // 分页参数兜底：负数交给数据库会直接报错，超大 pageSize 会拖垮查询
        int safePageNum = Math.max(pageNum, 1);
        int safePageSize = pageSize <= 0 ? 10 : Math.min(pageSize, MAX_PAGE_SIZE);
        String normalizedStatus = normalizeStatus(status);
        Page<OpenToolInvoke> page = invokeMapper.selectPage(new Page<>(safePageNum, safePageSize),
                new LambdaQueryWrapper<OpenToolInvoke>()
                        .eq(OpenToolInvoke::getTenantCode, effectiveTenant)
                        .eq(StringUtils.hasText(sessionId), OpenToolInvoke::getSessionId, sessionId)
                        .eq(normalizedStatus != null, OpenToolInvoke::getStatus, normalizedStatus)
                        .last("order by (expire_at is null) asc, expire_at asc, id desc"));

        List<ToolPendingDTO> records = new ArrayList<>(page.getRecords().size());
        for (OpenToolInvoke record : page.getRecords()) {
            records.add(toDto(record, false));
        }
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    /**
     * 待确认详情（含完整 requestJson，供确认人核对金额/券号等参数）。
     */
    public ToolPendingDTO detail(Long id, String tenantCode) {
        OpenToolInvoke record = require(id);
        assertTenant(record, tenantCode);
        return toDto(record, true);
    }

    // ==================== 确认 ====================

    /**
     * 坐席确认并执行。
     *
     * <p>顺序不能变：</p>
     * <ol>
     *   <li>已成功 → 直接重放首次结果，绝不二次执行；</li>
     *   <li>过期 → 置 EXPIRED 并拒绝（防「迟到的同意」触发扣款/发券）；</li>
     *   <li>幂等键已成功过 → 重放，不执行；</li>
     *   <li>乐观锁抢占 CONFIRMING → CONFIRMED，写 confirmed_by/at + 幂等键；</li>
     *   <li>抢占成功才出站；失败回写 FAILED。</li>
     * </ol>
     */
    public ToolInvokeResultDTO confirm(ToolConfirmDTO req) {
        if (req == null || req.getConfirmId() == null) {
            throw new BusinessException("确认ID不能为空");
        }
        String operator = requireOperator();
        OpenToolInvoke record = require(req.getConfirmId());
        assertTenant(record, null);

        // 1) 已成功：幂等重放。前端重复点、或坐席刷新后再点，都不该再执行一次
        if (ToolInvokeStatus.SUCCEEDED.name().equals(record.getStatus())) {
            return replay(record, "idempotent");
        }

        // 1.5) 已确认但结果未回写（执行中或进程中断）：只重放/提示，绝不二次执行。
        //      不处理这一分支的话，行会永久停在 CONFIRMED，坐席只会看到「已被处理」而无从判断
        if (ToolInvokeStatus.CONFIRMED.name().equals(record.getStatus())) {
            OpenToolInvoke done = StringUtils.hasText(record.getIdempotencyKey())
                    ? recorder.findSucceeded(record.getIdempotencyKey())
                    : null;
            if (done != null) {
                finish(record.getId(), ToolInvokeStatus.SUCCEEDED, done.getResponseJson(), 1);
                return replay(done, "idempotent");
            }
            throw new BusinessException("该确认正在执行中，请稍后刷新结果");
        }

        // 2) 惰性过期：读到时判定，不依赖定时任务
        record = expireIfNeeded(record);
        if (!ToolInvokeStatus.CONFIRMING.name().equals(record.getStatus())) {
            throw new BusinessException("该确认已被处理（当前状态：" + record.getStatus() + "）");
        }

        ToolInvokeDTO dto = parseRequest(record);
        String tenantCode = record.getTenantCode();
        String requestKey = StringUtils.hasText(req.getIdempotencyKey())
                ? req.getIdempotencyKey().trim()
                : null;
        if (requestKey != null && requestKey.length() > IDEMPOTENCY_KEY_MAX_LENGTH) {
            throw new BusinessException("幂等键长度不能超过 " + IDEMPOTENCY_KEY_MAX_LENGTH);
        }
        String idempotencyKey = requestKey != null
                ? requestKey
                : ToolInvokeSnapshot.idempotencyKey(tenantCode, dto);
        // 让执行链路与落库使用同一个键，避免「记录里是 A、出站/映射里是 B」的错位
        dto.setIdempotencyKey(idempotencyKey);

        // 3) 同键已成功：重放首次结果，同时把本次确认也标成功，保持审计连续
        OpenToolInvoke done = recorder.findSucceeded(idempotencyKey);
        if (done != null) {
            markConfirmed(record.getId(), operator, idempotencyKey);
            finish(record.getId(), ToolInvokeStatus.SUCCEEDED, done.getResponseJson(), 1);
            log.info("确认命中幂等键，重放首次结果 confirmId={} key={}", record.getId(), idempotencyKey);
            return replay(done, "idempotent");
        }

        // 4) 乐观锁抢占：条件 UPDATE，两个坐席同时点只有一个能成功
        int rows;
        try {
            rows = invokeMapper.update(null, new LambdaUpdateWrapper<OpenToolInvoke>()
                    .eq(OpenToolInvoke::getId, record.getId())
                    .eq(OpenToolInvoke::getStatus, ToolInvokeStatus.CONFIRMING.name())
                    .set(OpenToolInvoke::getStatus, ToolInvokeStatus.CONFIRMED.name())
                    .set(OpenToolInvoke::getConfirmedBy, operator)
                    .set(OpenToolInvoke::getConfirmedAt, LocalDateTime.now())
                    .set(OpenToolInvoke::getIdempotencyKey, idempotencyKey)
                    .set(OpenToolInvoke::getUpdateTime, LocalDateTime.now()));
        } catch (DataIntegrityViolationException e) {
            // 另一条记录已占用同一幂等键（不同 confirmId 的同一笔业务）：
            // 能成功就重放，不能就提示处理中，绝不重复执行
            OpenToolInvoke owner = recorder.findByIdempotencyKey(idempotencyKey);
            if (owner != null && ToolInvokeStatus.SUCCEEDED.name().equals(owner.getStatus())
                    && Integer.valueOf(1).equals(owner.getSuccess())) {
                finish(record.getId(), ToolInvokeStatus.SUCCEEDED, owner.getResponseJson(), 1);
                return replay(owner, "idempotent");
            }
            throw new BusinessException("相同操作正在处理中，请勿重复确认。");
        }
        if (rows == 0) {
            OpenToolInvoke latest = require(record.getId());
            if (ToolInvokeStatus.SUCCEEDED.name().equals(latest.getStatus())) {
                return replay(latest, "idempotent");
            }
            throw new BusinessException("该确认已被其他人处理（当前状态：" + latest.getStatus() + "）");
        }

        // 5) 抢占成功才出站执行
        try {
            ToolInvokeResultDTO result = openPlatformService.executeAfterConfirm(dto);
            finish(record.getId(), ToolInvokeStatus.SUCCEEDED, result.getOutput(), 1);
            result.setConfirmId(record.getId());
            result.setStatus(ToolInvokeStatus.SUCCEEDED.name());
            log.info("人工确认后执行成功 confirmId={} tool={} operator={}",
                    record.getId(), record.getToolName(), operator);
            return result;
        } catch (Exception e) {
            // 失败只改状态、不删记录：审计必须能查到「确认了但执行失败」
            log.warn("人工确认后执行失败 confirmId={} tool={}", record.getId(), record.getToolName(), e);
            finish(record.getId(), ToolInvokeStatus.FAILED, e.getMessage(), 0);
            throw e;
        }
    }

    // ==================== 拒绝 / 取消 ====================

    /**
     * 坐席拒绝：终态 REJECTED，用户可重新发起。
     */
    public void reject(ToolRejectDTO req) {
        if (req == null || req.getConfirmId() == null) {
            throw new BusinessException("确认ID不能为空");
        }
        String operator = requireOperator();
        OpenToolInvoke record = require(req.getConfirmId());
        assertTenant(record, null);
        record = expireIfNeeded(record);
        assertConfirming(record);

        int rows = invokeMapper.update(null, new LambdaUpdateWrapper<OpenToolInvoke>()
                .eq(OpenToolInvoke::getId, record.getId())
                .eq(OpenToolInvoke::getStatus, ToolInvokeStatus.CONFIRMING.name())
                .set(OpenToolInvoke::getStatus, ToolInvokeStatus.REJECTED.name())
                .set(OpenToolInvoke::getConfirmedBy, operator)
                .set(OpenToolInvoke::getConfirmedAt, LocalDateTime.now())
                .set(OpenToolInvoke::getRejectedReason, truncateReason(req.getReason()))
                .set(OpenToolInvoke::getUpdateTime, LocalDateTime.now()));
        if (rows == 0) {
            throw new BusinessException("该确认已被其他人处理");
        }
        log.info("坐席拒绝高危工具调用 confirmId={} tool={} operator={}",
                record.getId(), record.getToolName(), operator);
    }

    /**
     * 取消：发起方本人或具备确认权限的坐席。
     */
    public void cancel(ToolCancelDTO req) {
        if (req == null || req.getConfirmId() == null) {
            throw new BusinessException("确认ID不能为空");
        }
        String operator = requireOperator();
        OpenToolInvoke record = require(req.getConfirmId());
        assertTenant(record, null);

        boolean owner = StringUtils.hasText(record.getRequestedBy())
                && record.getRequestedBy().equals(operator);
        if (!owner && !JwtContext.hasPermission(PERMISSION_CONFIRM)) {
            throw new BusinessException("只能取消自己发起的操作");
        }
        record = expireIfNeeded(record);
        assertConfirming(record);

        int rows = invokeMapper.update(null, new LambdaUpdateWrapper<OpenToolInvoke>()
                .eq(OpenToolInvoke::getId, record.getId())
                .eq(OpenToolInvoke::getStatus, ToolInvokeStatus.CONFIRMING.name())
                .set(OpenToolInvoke::getStatus, ToolInvokeStatus.CANCELLED.name())
                .set(OpenToolInvoke::getConfirmedBy, operator)
                .set(OpenToolInvoke::getConfirmedAt, LocalDateTime.now())
                .set(OpenToolInvoke::getRejectedReason, truncateReason(req.getReason()))
                .set(OpenToolInvoke::getUpdateTime, LocalDateTime.now()));
        if (rows == 0) {
            throw new BusinessException("该确认已被处理，无法取消");
        }
        log.info("取消高危工具调用 confirmId={} tool={} operator={}",
                record.getId(), record.getToolName(), operator);
    }

    // ==================== 内部方法 ====================

    /**
     * 惰性过期：CONFIRMING 且 expire_at 已过 → 条件更新为 EXPIRED 并返回最新状态。
     * 条件更新保证并发下只有一次生效，其它请求读到 EXPIRED 后走「已被处理」分支。
     */
    private OpenToolInvoke expireIfNeeded(OpenToolInvoke record) {
        if (!ToolInvokeStatus.CONFIRMING.name().equals(record.getStatus())
                || record.getExpireAt() == null
                || !record.getExpireAt().isBefore(LocalDateTime.now())) {
            return record;
        }
        int rows = invokeMapper.update(null, new LambdaUpdateWrapper<OpenToolInvoke>()
                .eq(OpenToolInvoke::getId, record.getId())
                .eq(OpenToolInvoke::getStatus, ToolInvokeStatus.CONFIRMING.name())
                .set(OpenToolInvoke::getStatus, ToolInvokeStatus.EXPIRED.name())
                .set(OpenToolInvoke::getUpdateTime, LocalDateTime.now()));
        if (rows > 0) {
            log.info("待确认记录已超时失效 confirmId={} tool={}", record.getId(), record.getToolName());
            throw new BusinessException("确认已过期，请重新发起。");
        }
        return require(record.getId());
    }

    private void assertConfirming(OpenToolInvoke record) {
        if (!ToolInvokeStatus.CONFIRMING.name().equals(record.getStatus())) {
            throw new BusinessException("该确认已被处理（当前状态：" + record.getStatus() + "）");
        }
    }

    private void markConfirmed(Long id, String operator, String idempotencyKey) {
        try {
            invokeMapper.update(null, new LambdaUpdateWrapper<OpenToolInvoke>()
                    .eq(OpenToolInvoke::getId, id)
                    .eq(OpenToolInvoke::getStatus, ToolInvokeStatus.CONFIRMING.name())
                    .set(OpenToolInvoke::getStatus, ToolInvokeStatus.CONFIRMED.name())
                    .set(OpenToolInvoke::getConfirmedBy, operator)
                    .set(OpenToolInvoke::getConfirmedAt, LocalDateTime.now())
                    .set(OpenToolInvoke::getIdempotencyKey, idempotencyKey)
                    .set(OpenToolInvoke::getUpdateTime, LocalDateTime.now()));
        } catch (Exception e) {
            // 这里只用于「重放」场景，抢占失败不影响重放结果，记日志即可
            log.warn("重放时回写确认状态失败 confirmId={}", id, e);
        }
    }

    /**
     * 回写终态。落库失败只记日志：结果已经产生，不能因为审计写不进去让用户看到报错。
     */
    private void finish(Long id, ToolInvokeStatus status, String responseJson, int success) {
        try {
            invokeMapper.update(null, new LambdaUpdateWrapper<OpenToolInvoke>()
                    .eq(OpenToolInvoke::getId, id)
                    .set(OpenToolInvoke::getStatus, status.name())
                    .set(OpenToolInvoke::getResponseJson, responseJson == null ? "" : responseJson)
                    .set(OpenToolInvoke::getSuccess, success)
                    .set(OpenToolInvoke::getUpdateTime, LocalDateTime.now()));
        } catch (Exception e) {
            log.error("回写工具调用状态失败 confirmId={} status={}", id, status, e);
        }
    }

    /**
     * 从库内快照还原请求。以记录字段为准覆盖 toolName/sessionId/tenantCode，
     * 防止 request_json 被改动后打到别的工具上。
     */
    private ToolInvokeDTO parseRequest(OpenToolInvoke record) {
        if (!StringUtils.hasText(record.getRequestJson())) {
            throw new BusinessException("待确认记录缺少请求参数，无法执行");
        }
        try {
            ToolInvokeDTO dto = JSON.parseObject(record.getRequestJson(), ToolInvokeDTO.class);
            if (dto == null) {
                throw new BusinessException("待确认记录参数解析失败");
            }
            dto.setToolName(record.getToolName());
            dto.setSessionId(record.getSessionId());
            dto.setTenantCode(record.getTenantCode());
            dto.setConfirmed(true);
            return dto;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("待确认记录参数解析失败");
        }
    }

    private ToolInvokeResultDTO replay(OpenToolInvoke record, String source) {
        ToolInvokeResultDTO result = new ToolInvokeResultDTO();
        result.setSuccess(true);
        result.setToolName(record.getToolName());
        result.setOutput(record.getResponseJson() == null ? "" : record.getResponseJson());
        result.setSource(source);
        result.setConfirmId(record.getId());
        result.setStatus(record.getStatus());
        return result;
    }

    private OpenToolInvoke require(Long id) {
        if (id == null) {
            throw new BusinessException("确认ID不能为空");
        }
        OpenToolInvoke record = invokeMapper.selectById(id);
        if (record == null) {
            throw new BusinessException("待确认记录不存在");
        }
        return record;
    }

    /**
     * 租户校验：令牌带租户时以令牌为准，必须与记录租户一致；
     * 平台级账号（令牌未绑定租户）可跨租户运营，但若显式传了 tenantCode 也必须匹配。
     */
    private void assertTenant(OpenToolInvoke record, String requestTenantCode) {
        String tokenTenant = JwtContext.getCurrentTenantCode();
        String effective = StringUtils.hasText(tokenTenant)
                ? tokenTenant.trim()
                : (StringUtils.hasText(requestTenantCode) ? requestTenantCode.trim() : null);
        if (effective != null && !effective.equals(record.getTenantCode())) {
            throw new BusinessException("无权操作其他租户的确认记录");
        }
    }

    /**
     * 状态过滤值归一化：只接受状态机内的枚举名，非法值直接报错而不是查出空列表。
     */
    private String normalizeStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return null;
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        try {
            ToolInvokeStatus.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            throw new BusinessException("非法的状态过滤值：" + status);
        }
        return normalized;
    }

    /**
     * 取消/拒绝理由截断：列宽 255，超长直接让数据库报错会把整笔操作回滚，截断更符合审计诉求。
     */
    private String truncateReason(String reason) {
        if (!StringUtils.hasText(reason)) {
            return null;
        }
        String trimmed = reason.trim();
        return trimmed.length() <= REASON_MAX_LENGTH ? trimmed : trimmed.substring(0, REASON_MAX_LENGTH);
    }

    /**
     * 确认人必须是登录用户：访客令牌不得出现在确认链路上，否则等于 AI 代执行。
     */
    private String requireOperator() {
        String username = JwtContext.getCurrentUsername();
        if (!StringUtils.hasText(username)) {
            throw new BusinessException("必须由登录用户操作");
        }
        return username;
    }

    private ToolPendingDTO toDto(OpenToolInvoke record, boolean withRequestJson) {
        OpenToolInvoke current = record;
        // 列表/详情读取时惰性过期，避免只有定时任务才能发现超时
        if (ToolInvokeStatus.CONFIRMING.name().equals(current.getStatus())
                && current.getExpireAt() != null
                && current.getExpireAt().isBefore(LocalDateTime.now())) {
            int rows = invokeMapper.update(null, new LambdaUpdateWrapper<OpenToolInvoke>()
                    .eq(OpenToolInvoke::getId, current.getId())
                    .eq(OpenToolInvoke::getStatus, ToolInvokeStatus.CONFIRMING.name())
                    .set(OpenToolInvoke::getStatus, ToolInvokeStatus.EXPIRED.name())
                    .set(OpenToolInvoke::getUpdateTime, LocalDateTime.now()));
            if (rows > 0) {
                current.setStatus(ToolInvokeStatus.EXPIRED.name());
            }
        }

        ToolPendingDTO dto = new ToolPendingDTO();
        dto.setId(current.getId());
        dto.setToolName(current.getToolName());
        dto.setSessionId(current.getSessionId());
        dto.setStatus(current.getStatus());
        dto.setRisk(current.getRisk());
        dto.setRequestedBy(current.getRequestedBy());
        dto.setConfirmedBy(current.getConfirmedBy());
        dto.setConfirmedAt(current.getConfirmedAt());
        dto.setRejectedReason(current.getRejectedReason());
        dto.setExpireAt(current.getExpireAt());
        dto.setCreateTime(current.getCreateTime());
        if (withRequestJson) {
            dto.setRequestJson(current.getRequestJson());
        }
        // 参数摘要：只回白名单字段，前端不要直接渲染整个 payload
        ToolInvokeDTO req = parseQuietly(current.getRequestJson());
        if (req != null) {
            dto.setEntityId(req.getEntityId());
            dto.setEntityType(req.getEntityType());
            dto.setPayload(req.getPayload());
        }
        return dto;
    }

    private ToolInvokeDTO parseQuietly(String requestJson) {
        if (!StringUtils.hasText(requestJson)) {
            return null;
        }
        try {
            return JSON.parseObject(requestJson, ToolInvokeDTO.class);
        } catch (Exception e) {
            return null;
        }
    }
}
