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

    /** 从网关注入头还原当前用户，供审计与权限切面使用 */
    private static void bindFromGatewayHeaders(HttpServletRequest request) {
        String userId = request.getHeader("X-User-Id");
        String username = request.getHeader("X-Username");
        if (StringUtils.hasText(userId) && !"null".equals(userId)) {
            try {
                JwtContext.setCurrentUser(Long.parseLong(userId), username);
                return;
            } catch (NumberFormatException ignored) {
                // 非法 userId 时再尝试 JWT
            }
        }
        JwtContext.setFromRequest(request);
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
