package com.ai.cs.common.security;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 超级管理员判定与操作审计可见窗口：SUPER_ADMIN / SUPERADMIN（及种子账号 admin）可删全部日志，
 * 其他角色最多查看近一个月，更早的记录直接不可见。
 */
public final class SuperAdminAccess {

    /** 最高超级管理员角色编码（与前端 SUPER_ADMIN 对齐） */
    public static final String SUPER_ROLE = "SUPER_ADMIN";
    /** 兼容历史/演示数据里不带下划线的超管编码 */
    public static final String SUPER_ROLE_ALIAS = "SUPERADMIN";
    /** 初始化种子账号，尚未绑角色时也视为超管 */
    public static final String SEED_USERNAME = "admin";

    private SuperAdminAccess() {
    }

    /**
     * 角色编码是否超管：SUPER_ADMIN 或历史别名 SUPERADMIN（忽略大小写）。
     */
    public static boolean isSuperRoleCode(String roleCode) {
        if (roleCode == null || roleCode.isBlank()) {
            return false;
        }
        return SUPER_ROLE.equalsIgnoreCase(roleCode) || SUPER_ROLE_ALIAS.equalsIgnoreCase(roleCode);
    }

    /**
     * 是否最高超级管理员。角色编码为 SUPER_ADMIN / SUPERADMIN，或用户名为种子账号 admin。
     */
    public static boolean isSuperAdmin(String username, List<String> roles) {
        if (username != null && SEED_USERNAME.equalsIgnoreCase(username.trim())) {
            return true;
        }
        if (roles == null || roles.isEmpty()) {
            return false;
        }
        return roles.stream().anyMatch(r -> r != null && isSuperRoleCode(r.trim()));
    }

    /**
     * 非超管可查看的最早时间：当前时刻往前一个月；超管不限，返回 null。
     */
    public static LocalDateTime earliestViewTime(boolean superAdmin, LocalDateTime now) {
        if (superAdmin) {
            return null;
        }
        LocalDateTime clock = now != null ? now : LocalDateTime.now();
        return clock.minusMonths(1);
    }

    /**
     * 把查询起始时间限制在可见窗口内：超管尊重传入值；其他人不得早于一个月前。
     */
    public static LocalDateTime clampBegin(boolean superAdmin, LocalDateTime now, LocalDateTime beginTime) {
        LocalDateTime window = earliestViewTime(superAdmin, now);
        if (window == null) {
            return beginTime;
        }
        if (beginTime == null || beginTime.isBefore(window)) {
            return window;
        }
        return beginTime;
    }

    /**
     * 非超管且结束时间整段落在一个月窗口之前时，结果应为空。
     */
    public static boolean outOfViewWindow(boolean superAdmin, LocalDateTime now, LocalDateTime endTime) {
        if (superAdmin || endTime == null) {
            return false;
        }
        LocalDateTime window = earliestViewTime(false, now);
        return window != null && endTime.isBefore(window);
    }
}
