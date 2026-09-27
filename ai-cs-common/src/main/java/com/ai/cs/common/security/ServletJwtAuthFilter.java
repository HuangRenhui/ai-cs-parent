package com.ai.cs.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * 下游 Servlet 服务鉴权：拒绝绕过网关的裸奔请求。
 * 白名单放行；持有合法内部令牌时信任网关注入的身份头；否则必须带有效 JWT。
 */
@Slf4j
@Component
@Order(-90)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class ServletJwtAuthFilter extends OncePerRequestFilter {

    /** 无需登录即可访问的路径前缀（帮助中心、登录、探活、入站 Webhook 等） */
    private static final List<String> WHITE_LIST = List.of(
            "/auth/login",
            "/auth/register",
            "/help-center/",
            "/open/widget/init",
            "/open/webhook/inbound/receive",
            "/files/avatars/",
            "/actuator/",
            "/doc.html",
            "/webjars/",
            "/v3/api-docs",
            "/swagger-resources",
            "/swagger-ui",
            "/favicon.ico"
    );

    private final InternalAuthProperties internalAuthProperties;

    public ServletJwtAuthFilter(InternalAuthProperties internalAuthProperties) {
        this.internalAuthProperties = internalAuthProperties;
    }

    /**
     * 鉴权主流程：白名单 → 内部令牌（网关/Feign）→ JWT → 401。
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        try {
            if (isWhiteListed(path)) {
                JwtContext.setFromRequest(request);
                filterChain.doFilter(request, response);
                return;
            }
            // 网关或 Feign 携带内部令牌：信任 X-User-Id，避免每个下游再解析 JWT
            String internal = request.getHeader(InternalAuthProperties.HEADER);
            if (internalAuthProperties.matches(internal)) {
                bindFromGatewayHeaders(request);
                filterChain.doFilter(request, response);
                return;
            }
            // 直连下游时必须自带 JWT，防止伪造 X-User-Id
            if (JwtContext.setFromRequest(request)) {
                filterChain.doFilter(request, response);
                return;
            }
            log.warn("下游拒绝未授权访问: {} {}", request.getMethod(), path);
            writeUnauthorized(response);
        } finally {
            JwtContext.clear();
        }
    }

    /**
     * 从网关注入头还原当前用户（身份 + 租户 + 角色 + 权限），供审计、租户隔离与权限切面使用。
     *
     * <p>为什么可以信任这些头：只有携带合法内部令牌的请求才会走到这里，而内部令牌仅网关与 Feign 持有；
     * 直连下游的请求走的是 JWT 解析分支（见 {@link #doFilterInternal}），伪造请求头不会被采信。</p>
     *
     * <p>网关会把同名请求头先清空再写入，因此客户端自带的 {@code X-User-*} 不会残留。</p>
     */
    private static void bindFromGatewayHeaders(HttpServletRequest request) {
        String userId = request.getHeader("X-User-Id");
        String username = request.getHeader("X-Username");
        if (StringUtils.hasText(userId) && !"null".equals(userId)) {
            try {
                // 身份 + 租户：租户随网关透传，业务侧用 JwtContext.resolveTenantCode 取用
                JwtContext.setCurrentUser(Long.parseLong(userId), username, request.getHeader("X-Tenant-Id"));
                // 授权信息：网关从令牌 claim 解出后以逗号分隔透传，@RequirePermission 依赖它
                JwtContext.setRoles(splitHeader(request.getHeader("X-User-Roles")));
                JwtContext.setPermissions(splitHeader(request.getHeader("X-User-Perms")));
                return;
            } catch (NumberFormatException ignored) {
                // 非法 userId 时再尝试 JWT
            }
        }
        JwtContext.setFromRequest(request);
    }

    /**
     * 逗号分隔的身份头 → 字符串列表。
     *
     * <p>统一返回空列表而不是 null：{@code JwtContext.hasPermission} 按集合判空，
     * 返回 null 会退化成「无权限」，语义上一致，但空列表更不容易在别处触发 NPE。</p>
     */
    private static List<String> splitHeader(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }

    private static boolean isWhiteListed(String path) {
        if (path == null) {
            return false;
        }
        return WHITE_LIST.stream().anyMatch(path::startsWith);
    }

    /** 返回 JSON 401，避免默认 HTML 错误页 */
    private static void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"code\":401,\"msg\":\"未登录或登录已过期\",\"data\":null}");
    }
}
