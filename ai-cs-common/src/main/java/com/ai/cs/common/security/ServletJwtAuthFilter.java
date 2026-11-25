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
            // 归一化前置守卫：含 .. / 反斜杠 / 双斜杠的请求直接拒绝，避免白名单前缀被穿越路径绕过
            if (isSuspiciousPath(path)) {
                log.warn("拒绝可疑路径: {} {}", request.getMethod(), path);
                writeStatus(response, HttpServletResponse.SC_BAD_REQUEST, "非法请求路径");
                return;
            }
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
                // 令牌类型：区分坐席与匿名访客，业务侧据此做会话归属校验
                JwtContext.setTokenType(request.getHeader("X-Token-Type"));
                return;
            } catch (NumberFormatException ignored) {
                // 非法 userId 时再尝试 JWT
            }
        }
        // 内部调用（Feign/AI）可能没有用户上下文，此处保持匿名执行；需要身份的接口由 @RequirePermission 拦截
        if (!JwtContext.setFromRequest(request)) {
            log.debug("内部调用未携带用户身份: {} {}", request.getMethod(), request.getRequestURI());
        }
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
        // 按路径段匹配：/auth/login 命中 /auth/login/x，但不会命中 /auth/login-evil
        for (String prefix : WHITE_LIST) {
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

    /** 可疑路径：目录穿越、反斜杠、重复斜杠、编码后的点/反斜杠 */
    private static boolean isSuspiciousPath(String path) {
        if (path == null || path.isEmpty() || path.indexOf('\0') >= 0) {
            return true;
        }
        String lower = path.toLowerCase(java.util.Locale.ROOT);
        return lower.contains("..") || lower.contains("\\") || lower.contains("//")
                || lower.contains("%2e") || lower.contains("%5c");
    }

    /** 返回 JSON 401，避免默认 HTML 错误页 */
    private static void writeUnauthorized(HttpServletResponse response) throws IOException {
        writeStatus(response, HttpServletResponse.SC_UNAUTHORIZED, "未登录或登录已过期");
    }

    /** 统一 JSON 错误响应 */
    private static void writeStatus(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"code\":" + status + ",\"msg\":\"" + message + "\",\"data\":null}");
    }
}
