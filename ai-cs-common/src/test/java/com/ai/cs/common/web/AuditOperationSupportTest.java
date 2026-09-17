package com.ai.cs.common.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 管理员操作审计文案与脱敏。
 */
@DisplayName("AuditOperationSupport")
class AuditOperationSupportTest {

    @Test
    @DisplayName("删除知识库应记成可读操作，并归到 knowledge 模块")
    void describeDeleteKnowledge() {
        assertTrue(AuditOperationSupport.shouldAudit("DELETE", "/knowledge/delete/3"));
        assertEquals("knowledge", AuditOperationSupport.moduleOf("/knowledge/delete/3"));
        assertEquals("删除知识库", AuditOperationSupport.operationOf("DELETE", "/knowledge/delete/3"));
    }

    @Test
    @DisplayName("系统用户保存归到 user 模块，而不是笼统的 system")
    void describeSystemUser() {
        assertEquals("user", AuditOperationSupport.moduleOf("/system/user/save"));
        assertEquals("保存用户", AuditOperationSupport.operationOf("POST", "/system/user/save"));
    }

    @Test
    @DisplayName("聊天与平台登录不记管理员审计")
    void skipChatAndPlatformLogin() {
        assertFalse(AuditOperationSupport.shouldAudit("POST", "/ai/chat"));
        assertFalse(AuditOperationSupport.shouldAudit("POST", "/auth/login/platform"));
        assertTrue(AuditOperationSupport.shouldAudit("POST", "/auth/login"));
        assertEquals("管理员登录", AuditOperationSupport.operationOf("POST", "/auth/login"));
    }

    @Test
    @DisplayName("请求体里的密码要打码")
    void redactPassword() {
        String out = AuditOperationSupport.redactParams("{\"username\":\"admin\",\"password\":\"secret-1\"}");
        assertNotNull(out);
        assertTrue(out.contains("admin"));
        assertTrue(out.contains("***"));
        assertFalse(out.contains("secret-1"));
        assertEquals("admin", AuditOperationSupport.usernameFromParams(out));
    }
}
