package com.ai.cs.base.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 内部令牌验证过滤器
 * 验证请求是否来自网关，防止直连绕过网关的安全检查
 *
 * @author huangrenhui
 * @date 2026/9/11
 */
@Slf4j
@Component
@Order(1)
public class InternalTokenValidationFilter extends OncePerRequestFilter {

    private static final String INTERNAL_HEADER = "X-Internal-Token";
    private static final String DEFAULT_INTERNAL = "AiCsInternalToken2026LocalOnly";

    @Value("${ai.internal.token:${INTERNAL_API_TOKEN:}}")
    private String internalToken;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();

        // 白名单路径跳过验证（健康检查、文档等）
        if (isWhitelisted(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = request.getHeader(INTERNAL_HEADER);
        String expectedToken = resolveInternalToken();

        // 如果配置了内部令牌，则必须验证
        if (expectedToken != null && !expectedToken.isBlank() && !expectedToken.equals(token)) {
            log.warn("无效的内部令牌，路径：{}，来源IP：{}", path, request.getRemoteAddr());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("Unauthorized: Invalid internal token");
            return;
        }

        filterChain.doFilter(request, response);
    }

    /** 解析内部令牌：配置 → 环境变量 → 本机默认 */
    private String resolveInternalToken() {
        if (internalToken != null && !internalToken.isBlank()) {
            return internalToken.trim();
        }
        String env = System.getenv("INTERNAL_API_TOKEN");
        if (env != null && !env.isBlank()) {
            return env.trim();
        }
        return DEFAULT_INTERNAL;
    }

    /** 白名单路径（无需内部令牌验证） */
    private boolean isWhitelisted(String path) {
        return path.startsWith("/actuator/health") ||
               path.startsWith("/doc.html") ||
               path.startsWith("/webjars/") ||
               path.startsWith("/v3/api-docs/") ||
               path.startsWith("/swagger-resources/") ||
               path.startsWith("/swagger-ui/") ||
               path.startsWith("/auth/login") ||
               path.startsWith("/auth/register");
    }
}
