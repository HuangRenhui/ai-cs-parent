package com.ai.cs.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JwtUtil 单元测试
 *
 * @author huangrenhui
 * @date 2026/7/20
 */
@DisplayName("JwtUtil 单元测试")
class JwtUtilTest {

    private static final Long TEST_USER_ID = 1001L;
    private static final String TEST_USERNAME = "admin";

    @Test
    @DisplayName("生成Token - 默认过期时间")
    void testGenerateToken_DefaultExpire() {
        String token = JwtUtil.generateToken(TEST_USER_ID, TEST_USERNAME);
        assertNotNull(token);
        assertTrue(token.length() > 0);
    }

    @Test
    @DisplayName("生成Token - 自定义过期时间")
    void testGenerateToken_CustomExpire() {
        long expireMs = 60 * 60 * 1000L;
        String token = JwtUtil.generateToken(TEST_USER_ID, TEST_USERNAME, expireMs);
        assertNotNull(token);
    }

    @Test
    @DisplayName("解析Token - 正常场景")
    void testParseToken_Valid() {
        String token = JwtUtil.generateToken(TEST_USER_ID, TEST_USERNAME);
        var claims = JwtUtil.parseToken(token);
        assertNotNull(claims);
        assertEquals(TEST_USER_ID.toString(), claims.getSubject());
    }

    @Test
    @DisplayName("解析Token - null输入")
    void testParseToken_Null() {
        assertNull(JwtUtil.parseToken(null));
    }

    @Test
    @DisplayName("解析Token - 空字符串")
    void testParseToken_Blank() {
        assertNull(JwtUtil.parseToken(""));
        assertNull(JwtUtil.parseToken("   "));
    }

    @Test
    @DisplayName("解析Token - 无效Token")
    void testParseToken_Invalid() {
        assertNull(JwtUtil.parseToken("invalid.token.here"));
    }

    @Test
    @DisplayName("获取UserId - 正常场景")
    void testGetUserId() {
        String token = JwtUtil.generateToken(TEST_USER_ID, TEST_USERNAME);
        assertEquals(TEST_USER_ID, JwtUtil.getUserId(token));
    }

    @Test
    @DisplayName("获取UserId - 无效Token返回null")
    void testGetUserId_InvalidToken() {
        assertNull(JwtUtil.getUserId(null));
        assertNull(JwtUtil.getUserId("invalid"));
    }

    @Test
    @DisplayName("获取Username - 正常场景")
    void testGetUsername() {
        String token = JwtUtil.generateToken(TEST_USER_ID, TEST_USERNAME);
        assertEquals(TEST_USERNAME, JwtUtil.getUsername(token));
        assertEquals(JwtUtil.TYP_STAFF, JwtUtil.getTokenType(token));
        assertFalse(JwtUtil.isVisitor(token));
    }

    @Test
    @DisplayName("访客令牌带 visitor 类型")
    void testVisitorToken() {
        String token = JwtUtil.generateVisitorToken(0L, "visitor:anon");
        assertTrue(JwtUtil.isVisitor(token));
        assertEquals(JwtUtil.TYP_VISITOR, JwtUtil.getTokenType(token));
        assertEquals("visitor:anon", JwtUtil.getUsername(token));
    }
}
