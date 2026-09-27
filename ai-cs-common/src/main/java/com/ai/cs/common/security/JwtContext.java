package com.ai.cs.common.security;

import com.ai.cs.common.util.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;

/**
 * JWT请求上下文，存储当前请求的用户信息
 *
 * <p>数据来源有两条，最终都落到这里的 ThreadLocal，供业务与鉴权切面读取：</p>
 * <ol>
 *   <li><b>网关转发</b>：网关校验 JWT 后把身份与授权信息写进请求头（{@code X-User-Id} / {@code X-Tenant-Id} /
 *       {@code X-User-Roles} / {@code X-User-Perms}），下游过滤器 {@link ServletJwtAuthFilter} 还原；</li>
 *   <li><b>直连下游</b>：不走网关时（本机调试、Feign 之外调用）自己解析 JWT claim 还原，
 *       这样「客户端伪造请求头」不会生效。</li>
 * </ol>
 *
 * @author huangrenhui
 * @date 2026/7/20
 */
@Slf4j
public class JwtContext {

    // 全部基于 ThreadLocal：一次请求一个线程，请求结束必须调用 clear() 防线程复用串号
    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> USERNAME = new ThreadLocal<>();
    /** 当前请求生效的租户（来自令牌；平台级账号为空） */
    private static final ThreadLocal<String> TENANT_CODE = new ThreadLocal<>();
    private static final ThreadLocal<List<String>> PERMISSIONS = new ThreadLocal<>();
    private static final ThreadLocal<List<String>> ROLES = new ThreadLocal<>();

    /** 默认租户：所有租户相关字段为空时的兜底值，避免拼出非法集合名或查全表 */
    public static final String DEFAULT_TENANT = "default";

    /** 写入当前登录用户（认证过滤器在鉴权通过后调用） */
    public static void setCurrentUser(Long userId, String username) {
        USER_ID.set(userId);
        USERNAME.set(username);
    }

    /**
     * 写入当前登录用户（含租户）。
     *
     * @param tenantCode 令牌绑定租户；平台级账号传 null/空，表示可跨租户运营
     */
    public static void setCurrentUser(Long userId, String username, String tenantCode) {
        setCurrentUser(userId, username);
        setTenant(tenantCode);
    }

    /** 写入当前请求生效的租户；空白值忽略（保留原值，避免被下游覆盖成空） */
    public static void setTenant(String tenantCode) {
        if (StringUtils.hasText(tenantCode)) {
            TENANT_CODE.set(tenantCode.trim());
        }
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

    /** 当前生效租户；令牌未绑定租户（平台级账号）时返回 null */
    public static String getCurrentTenantCode() {
        return TENANT_CODE.get();
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
        return perms.contains(permission) || perms.contains(JwtUtil.WILDCARD_PERMISSION);
    }

    /**
     * 解析本次请求真正应使用的租户编码（<b>防水平越权的关键</b>）。
     *
     * <p>规则：</p>
     * <ol>
     *   <li>令牌里带租户（坐席、访客等绑定租户的令牌）→ <b>一律以令牌为准</b>，忽略请求参数，
     *       这样「改 URL/表单里的 tenantCode」无法读到别的租户数据；</li>
     *   <li>令牌未绑定租户（平台级运营账号、白名单匿名接口）→ 回退到请求参数；</li>
     *   <li>都为空 → 兜底 {@link #DEFAULT_TENANT}，与数据库与集合命名的默认值保持一致。</li>
     * </ol>
     *
     * @param requestTenantCode 请求参数里的租户编码（可为空）
     * @return 本次请求生效的租户编码，永不为空
     */
    public static String resolveTenantCode(String requestTenantCode) {
        String tokenTenant = getCurrentTenantCode();
        if (StringUtils.hasText(tokenTenant)) {
            return tokenTenant.trim();
        }
        return StringUtils.hasText(requestTenantCode) ? requestTenantCode.trim() : DEFAULT_TENANT;
    }

    /**
     * 同 {@link #resolveTenantCode(String)}，但**不兜底 default**：令牌与请求参数都没有租户时返回 null。
     *
     * <p>供「必须显式指定租户」的接口做参数校验用——这类接口宁可报错，也不要悄悄去查 default 租户的数据。</p>
     */
    public static String resolveTenantCodeOrNull(String requestTenantCode) {
        String tokenTenant = getCurrentTenantCode();
        if (StringUtils.hasText(tokenTenant)) {
            return tokenTenant.trim();
        }
        return StringUtils.hasText(requestTenantCode) ? requestTenantCode.trim() : null;
    }

    /** 清理 ThreadLocal：过滤器 finally 中必须调用，防止线程池复用导致的数据串号与内存泄漏 */
    public static void clear() {
        USER_ID.remove();
        USERNAME.remove();
        TENANT_CODE.remove();
        PERMISSIONS.remove();
        ROLES.remove();
    }

    /**
     * 从请求中设置JWT上下文（含租户、角色、权限）
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
        // 授权与租户同样从令牌还原：它们是签发时写死的，客户端无法篡改
        setTenant(JwtUtil.getTenantCode(token));
        setRoles(JwtUtil.getRoles(token));
        setPermissions(JwtUtil.getPermissions(token));
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
