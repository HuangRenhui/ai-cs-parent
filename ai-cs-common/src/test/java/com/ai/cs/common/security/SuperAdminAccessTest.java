package com.ai.cs.common.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 超级管理员判定与操作审计一个月可见窗口。
 */
@DisplayName("SuperAdminAccess")
class SuperAdminAccessTest {

    @Test
    @DisplayName("SUPER_ADMIN 与种子账号 admin 视为超管，ADMIN 不是")
    void detectSuperAdmin() {
        assertTrue(SuperAdminAccess.isSuperAdmin("admin", List.of()));
        assertTrue(SuperAdminAccess.isSuperAdmin("operator", List.of("SUPER_ADMIN")));
        assertTrue(SuperAdminAccess.isSuperAdmin("operator", List.of("SUPERADMIN")));
        assertFalse(SuperAdminAccess.isSuperAdmin("operator", List.of("ADMIN")));
        assertFalse(SuperAdminAccess.isSuperAdmin("operator", null));
    }

    @Test
    @DisplayName("非超管查询起始不得早于一个月，超管不裁剪")
    void clampBeginForViewer() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 11, 12, 0);
        LocalDateTime old = LocalDateTime.of(2026, 6, 1, 0, 0);
        assertEquals(old, SuperAdminAccess.clampBegin(true, now, old));
        assertEquals(now.minusMonths(1), SuperAdminAccess.clampBegin(false, now, old));
        assertEquals(now.minusMonths(1), SuperAdminAccess.clampBegin(false, now, null));
        LocalDateTime within = now.minusDays(3);
        assertEquals(within, SuperAdminAccess.clampBegin(false, now, within));
    }

    @Test
    @DisplayName("结束时间整段落在一个月外时，非超管应得到空结果")
    void outOfWindow() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 11, 12, 0);
        assertTrue(SuperAdminAccess.outOfViewWindow(false, now, now.minusMonths(2)));
        assertFalse(SuperAdminAccess.outOfViewWindow(false, now, now.minusDays(2)));
        assertFalse(SuperAdminAccess.outOfViewWindow(true, now, now.minusMonths(2)));
    }
}
