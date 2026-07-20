package com.ai.cs.common.security;

import com.ai.cs.common.util.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * JWT请求上下文，存储当前请求的用户信息
 *
 * @author huangrenhui
 * @date 2026/7/20
 */
@Slf4j
public class JwtContext {

    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> USERNAME = new ThreadLocal<>();
    private static final ThreadLocal<List<String>> PERMISSIONS = new ThreadLocal<>();
    private static final ThreadLocal<List<String>> ROLES = new ThreadLocal<>();

    public static void setCurrentUser(Long userId, String username) {
        USER_ID.set(userId);
        USERNAME.set(username);
    }

    public static void setPermissions(List<String> permissions) {
        PERMISSIONS.set(permissions);
    }

    public static void setRoles(List<String> roles) {
        ROLES.set(roles);
    }

    public static Long getCurrentUserId() {
        return USER_ID.get();
    }

    public static String getCurrentUsername() {
        return USERNAME.get();
    }

    public static List<String> getCurrentPermissions() {
        return PERMISSIONS.get() != null ? PERMISSIONS.get() : Collections.emptyList();
    }

    public static List<String> getCurrentRoles() {
        return ROLES.get() != null ? ROLES.get() : Collections.emptyList();
    }

    public static boolean hasPermission(String permission) {
        List<String> perms = getCurrentPermissions();
        return perms.contains(permission) || perms.contains("*:*:*");
    }

    public static void clear() {
        USER_ID.remove();
        USERNAME.remove();
        PERMISSIONS.remove();
        ROLES.remove();
    }

    /**
     * 从请求中设置JWT上下文
     */
    public static boolean setFromRequest(HttpServletRequest request) {
        String token = extractToken(request);
        if (token == null) {
            return false;
        }
        Claims claims = JwtUtil.parseToken(token);
        if (claims == null) {
            return false;
        }
        Long userId = Long.parseLong(claims.getSubject());
        String username = claims.get("username", String.class);
        setCurrentUser(userId, username);
        return true;
    }

    /**
     * 从请求中提取JWT Token
     */
    public static String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return request.getParameter("token");
    }
}
