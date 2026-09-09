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

    // 全部基于 ThreadLocal：一次请求一个线程，请求结束必须调用 clear() 防线程复用串号
    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> USERNAME = new ThreadLocal<>();
    private static final ThreadLocal<List<String>> PERMISSIONS = new ThreadLocal<>();
    private static final ThreadLocal<List<String>> ROLES = new ThreadLocal<>();

    /** 写入当前登录用户（认证过滤器在鉴权通过后调用） */
    public static void setCurrentUser(Long userId, String username) {
        USER_ID.set(userId);
        USERNAME.set(username);
    }

    /** 写入当前用户权限标识列表 */
    public static void setPermissions(List<String> permissions) {
        PERMISSIONS.set(permissions);
    }

    /** 写入当前用户角色列表 */
    public static void setRoles(List<String> roles) {
        ROLES.set(roles);
    }

    /** 当前用户ID，未登录返回 null */
    public static Long getCurrentUserId() {
        return USER_ID.get();
    }

    /** 当前用户名，未登录返回 null */
    public static String getCurrentUsername() {
        return USERNAME.get();
    }

    /** 当前用户权限列表，未登录返回空列表 */
    public static List<String> getCurrentPermissions() {
        return PERMISSIONS.get() != null ? PERMISSIONS.get() : Collections.emptyList();
    }

    /** 当前用户角色列表，未登录返回空列表 */
    public static List<String> getCurrentRoles() {
        return ROLES.get() != null ? ROLES.get() : Collections.emptyList();
    }

    /** 是否拥有指定权限；*:*:* 为超级管理员通配权限 */
    public static boolean hasPermission(String permission) {
        List<String> perms = getCurrentPermissions();
        return perms.contains(permission) || perms.contains("*:*:*");
    }

    /** 清理 ThreadLocal：过滤器 finally 中必须调用，防止线程池复用导致的数据串号与内存泄漏 */
    public static void clear() {
        USER_ID.remove();
        USERNAME.remove();
        PERMISSIONS.remove();
        ROLES.remove();
    }

    /**
     * 从请求中设置JWT上下文
     *
     * @return true=token 有效且上下文已写入；false=无 token 或 token 无效
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
        // subject 即 userId（生成 token 时的约定）
        Long userId = Long.parseLong(claims.getSubject());
        String username = claims.get("username", String.class);
        setCurrentUser(userId, username);
        return true;
    }

    /**
     * 从请求中提取JWT Token：优先取 Authorization: Bearer 头，其次取 ?token= 参数（WebSocket 等场景）
     */
    public static String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            // "Bearer " 共 7 个字符
            return bearerToken.substring(7);
        }
        return request.getParameter("token");
    }
}
