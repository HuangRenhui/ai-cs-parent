package com.ai.cs.base.service;

import com.ai.cs.base.entity.OperationLog;
import com.ai.cs.base.mapper.OperationLogMapper;
import com.ai.cs.base.mapper.UserMapper;
import com.ai.cs.common.dto.AccessAnalysisDTO;
import com.ai.cs.common.exception.BusinessException;
import com.ai.cs.common.security.JwtContext;
import com.ai.cs.common.security.SuperAdminAccess;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * 操作日志服务：超管可查可删全部记录；其他角色只能看近一个月且不能删除。
 * 流量分析按同一套时间窗口聚合请求量与来源 IP。
 *
 * @author huangrenhui
 * @date 2026/7/20
 */
@Service
public class OperationLogService extends ServiceImpl<OperationLogMapper, OperationLog> {

    /** 流量分析一次最多读这么多条，避免把全表拉进内存 */
    private static final int ANALYSIS_LIMIT = 8000;

    @Resource
    private UserMapper userMapper;

    /**
     * 分页查询操作日志。非超管强制裁剪到近一个月，更早的记录不会出现在结果里。
     */
    public Page<OperationLog> queryPage(int pageNum, int pageSize, Long userId, String module,
                                        String username, String ip, LocalDateTime beginTime, LocalDateTime endTime) {
        boolean superAdmin = currentUserIsSuperAdmin();
        LocalDateTime now = LocalDateTime.now();
        // 结束时间整段落在一个月窗口之前：直接空页，避免把历史记录漏出去
        if (SuperAdminAccess.outOfViewWindow(superAdmin, now, endTime)) {
            Page<OperationLog> empty = new Page<>(pageNum, pageSize);
            empty.setRecords(Collections.emptyList());
            empty.setTotal(0);
            return empty;
        }
        LocalDateTime from = SuperAdminAccess.clampBegin(superAdmin, now, beginTime);
        LambdaQueryWrapper<OperationLog> wrapper = new LambdaQueryWrapper<>();
        if (userId != null) {
            wrapper.eq(OperationLog::getUserId, userId);
        }
        if (module != null && !module.isBlank()) {
            wrapper.eq(OperationLog::getModule, module);
        }
        if (username != null && !username.isBlank()) {
            wrapper.like(OperationLog::getUsername, username.trim());
        }
        // 精确匹配来源 IP，供流量分析页点进审计列表
        if (ip != null && !ip.isBlank()) {
            wrapper.eq(OperationLog::getIp, ip.trim());
        }
        if (from != null) {
            wrapper.ge(OperationLog::getCreateTime, from);
        }
        if (endTime != null) {
            wrapper.le(OperationLog::getCreateTime, endTime);
        }
        wrapper.orderByDesc(OperationLog::getCreateTime);
        return this.page(new Page<>(pageNum, pageSize), wrapper);
    }

    /**
     * 按时间窗口汇总请求量、来源 IP 和模块分布（占位）
     *
     * <p>TODO 后续实现：开始时间为空时默认近 7 天，并用 {@code SuperAdminAccess.clampBegin} 按角色裁剪
     * （非超管只能看近一个月），然后按窗口查审计记录（一次最多读 ANALYSIS_LIMIT 条，避免把全表拉进内存），
     * 交给 {@code AccessAnalysisSupport} 聚合出趋势、IP 排行与模块分布。</p>
     *
     * @param beginTime 窗口起点，空则近 7 天
     * @param endTime   窗口终点，空则不限制
     * @return 占位返回空分析结果
     */
    public AccessAnalysisDTO analyze(LocalDateTime beginTime, LocalDateTime endTime) {
        boolean superAdmin = currentUserIsSuperAdmin();
        LocalDateTime now = LocalDateTime.now();
        // 结束时间整段落在可见窗口之前：直接空结果（权限可见窗口判定保留）
        if (SuperAdminAccess.outOfViewWindow(superAdmin, now, endTime)) {
            return new AccessAnalysisDTO();
        }
        return AccessAnalysisSupport.from(List.of());
    }

    /**
     * 删除单条审计。仅最高超级管理员可执行，其他人一律拒绝。
     */
    public void deleteById(Long id) {
        assertSuperAdminCanDelete();
        if (id == null) {
            throw new BusinessException("请选择要删除的记录");
        }
        this.removeById(id);
    }

    /**
     * 当前登录人是否最高超级管理员（角色 SUPER_ADMIN 或种子账号 admin）。
     */
    public boolean currentUserIsSuperAdmin() {
        Long userId = JwtContext.getCurrentUserId();
        String username = JwtContext.getCurrentUsername();
        List<String> roles = JwtContext.getCurrentRoles();
        // 过滤器通常不注入角色，按用户 ID 查库，避免前端改角色编码绕过
        if ((roles == null || roles.isEmpty()) && userId != null && userMapper != null) {
            List<String> dbRoles = userMapper.selectRolesByUserId(userId);
            roles = dbRoles != null ? dbRoles : List.of();
        }
        return SuperAdminAccess.isSuperAdmin(username, roles);
    }

    /** 非超管删除审计时直接 403，前端按钮隐藏仍以服务端为准 */
    private void assertSuperAdminCanDelete() {
        if (!currentUserIsSuperAdmin()) {
            throw new BusinessException(403, "仅超级管理员可删除操作审计");
        }
    }
}
