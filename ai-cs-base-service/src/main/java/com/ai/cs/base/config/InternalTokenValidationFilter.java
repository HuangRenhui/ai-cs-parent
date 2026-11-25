package com.ai.cs.base.config;

import com.ai.cs.common.security.InternalAuthProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 内部令牌验证过滤器
 * 验证请求是否来自网关，防止直连绕过网关的安全检查
 *
 * <p>令牌解析与生产环境强校验统一收敛到 {@link InternalAuthProperties}，
 * 避免各过滤器各自维护一份「本机默认令牌」导致口径不一致。</p>
 *
 * <p>只在 {@code SecurityFilterChain} 中注册一次（不标 {@code @Component}），
 * 否则 Servlet 容器会再注册一遍，导致每个请求重复校验。</p>
 *
 * @author huangrenhui
 * @date 2026/9/11
 */
@Slf4j
public class InternalTokenValidationFilter extends OncePerRequestFilter {

    private final InternalAuthProperties internalAuthProperties;

    public InternalTokenValidationFilter(InternalAuthProperties internalAuthProperties) {
        this.internalAuthProperties = internalAuthProperties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();

        // 含 .. / 反斜杠 / 双斜杠的请求直接拒绝，避免白名单前缀被穿越路径绕过
        if (path != null && (path.contains("..") || path.contains("\\") || path.contains("//")
                || path.toLowerCase(java.util.Locale.ROOT).contains("%2e"))) {
            log.warn("拒绝可疑路径，来源IP：{}，路径：{}", request.getRemoteAddr(), path);
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        // 白名单路径跳过验证（健康检查、文档等）
        if (isWhitelisted(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = request.getHeader(InternalAuthProperties.HEADER);
        if (!internalAuthProperties.matches(token)) {
            log.warn("无效的内部令牌，路径：{}，来源IP：{}", path, request.getRemoteAddr());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setCharacterEncoding("UTF-8");
            response.setContentType("application/json");
            response.getWriter().write("{\"code\":401,\"msg\":\"Unauthorized: Invalid internal token\",\"data\":null}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    /** 白名单路径（无需内部令牌验证） */
    private boolean isWhitelisted(String path) {
        if (path == null || path.isEmpty()) {
            return false;
        }
        String[] prefixes = {"/actuator/health", "/doc.html", "/webjars/", "/v3/api-docs/",
                "/swagger-resources/", "/swagger-ui/", "/auth/login", "/auth/register"};
        for (String prefix : prefixes) {
            if (path.equals(prefix)) {
                return true;
            }
            String base = prefix.endsWith("/") ? prefix : prefix + "/";
            if (path.startsWith(base)) {
                return true;
            }
        }
        return false;
    }
}
