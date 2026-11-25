package com.ai.cs.gateway.filter;

import com.ai.cs.common.util.JwtUtil;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Gateway JWT 全局过滤器。访客令牌只能访问聊天相关路径。
 */
@Slf4j
@Component
public class JwtAuthFilter implements GlobalFilter, Ordered {

    /** 白名单路径（前缀匹配）：登录、注册、WebSocket、健康检查、入站 Webhook 等无需认证即可访问 */
    private static final List<String> WHITE_LIST = List.of(
            "/api/auth/login",
            "/api/auth/register",
            "/auth/login",
            "/auth/register",
            "/ws/",
            "/open/widget/init",
            "/open/webhook/inbound/receive",
            "/ops/health/self",
            "/knowledge/health",
            "/help-center/",
            "/files/avatars/",
            // 对话附件静态资源（上传后的图片/文件需可被直接访问展示）
            "/files/chat/",
            "/actuator/health"
    );

    /** 访客令牌允许访问的路径（前缀匹配）：只开放聊天、会话与对话附件上传 */
    private static final List<String> VISITOR_ALLOWED = List.of(
            "/ai/chat",
            "/session/ensure",
            "/session/message",
            // 对话附件上传（图片/文档等），C 端未登录用户也需可传
            "/file/chat-attachment"
    );

    private static final String INTERNAL_HEADER = "X-Internal-Token";
    /** 仅 dev/local/test 允许使用的演示内部令牌 */
    private static final String DEV_DEFAULT_INTERNAL = "AiCsInternalToken2026LocalOnly";

    /**
     * 身份类请求头：一律由网关按令牌重新写入，进入下游前先清空客户端自带的同名头。
     *
     * <p>不清空的后果：调用方只要自己带上 {@code X-User-Id: 1}，而下游在「内部令牌」分支信任这些头，
     * 就能冒充任意用户（越权）。</p>
     */
    private static final List<String> IDENTITY_HEADERS = List.of(
            "X-User-Id", "X-Username", "X-Token-Type", "X-Tenant-Id", "X-User-Roles", "X-User-Perms");

    @Value("${ai.internal.token:${INTERNAL_API_TOKEN:}}")
    private String internalToken;

    /** 当前激活的 Spring 配置，用于拒绝在生产/未指定 profile 时使用演示令牌 */
    @Value("${spring.profiles.active:}")
    private String activeProfile;

    /** 启动时解析并冻结的内部令牌 */
    private volatile String resolvedInternalToken;
    /** 当前是否为 dev/local/test（允许演示值）；启动时确定 */
    private volatile boolean lenientEnv;

    /**
     * 启动校验：显式配置优先；生产、预发与空 profile 必须配置，否则网关拒绝启动。
     * 只有 dev/local/test 才允许回退到演示令牌。
     */
    @jakarta.annotation.PostConstruct
    public void initInternalToken() {
        lenientEnv = com.ai.cs.common.security.SecretPolicy.isLenient(activeProfile);
        String configured = internalToken != null && !internalToken.isBlank() ? internalToken.trim() : null;
        if (configured == null) {
            String env = System.getenv("INTERNAL_API_TOKEN");
            configured = env != null && !env.isBlank() ? env.trim() : null;
        }
        if (configured == null) {
            if (!lenientEnv) {
                throw new IllegalStateException(
                        "网关未配置 INTERNAL_API_TOKEN / ai.internal.token（当前 profile=" + activeProfile
                                + "）。请显式注入内部令牌，或本地开发使用 dev/local profile。");
            }
            resolvedInternalToken = DEV_DEFAULT_INTERNAL;
            return;
        }
        assertAcceptableInternalToken(configured);
        resolvedInternalToken = configured;
    }

    /** 严格模式拒绝演示令牌与过短令牌（环境变量路径同样校验，避免只绕过配置项） */
    private void assertAcceptableInternalToken(String value) {
        if (lenientEnv) {
            return;
        }
        if (DEV_DEFAULT_INTERNAL.equals(value)) {
            throw new IllegalStateException("禁止在生产/未指定 profile 的环境使用内置默认内部令牌");
        }
        if (value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < 16) {
            throw new IllegalStateException("INTERNAL_API_TOKEN 过短，生产环境要求至少 16 字节");
        }
    }

    /** 解析内部令牌（启动时已确定；未初始化时 fail-closed） */
    private String resolveInternalToken() {
        if (resolvedInternalToken != null) {
            return resolvedInternalToken;
        }
        if (!lenientEnv) {
            throw new IllegalStateException("内部令牌尚未初始化，拒绝使用内置默认值");
        }
        return DEV_DEFAULT_INTERNAL;
    }

    /**
     * JWT 全局鉴权：白名单放行（仍注入内部令牌）→ 校验令牌 → 访客路径限制 → 注入用户身份与内部令牌
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        // 路径归一化前置守卫：含 .. / 反斜杠 / 双斜杠的请求一律拒绝。
        // 否则 /files/avatars/../../system/user/list 这类路径会先命中白名单前缀，再被路由到受保护服务。
        if (isSuspiciousPath(path)) {
            log.warn("拒绝可疑路径: {}", path);
            exchange.getResponse().setStatusCode(HttpStatus.BAD_REQUEST);
            return exchange.getResponse().setComplete();
        }

        // 白名单直接放行，但仍注入内部令牌，便于下游识别来自网关而不是直连伪造；
        // 同时清掉客户端自带的身份头，避免白名单接口被伪造身份调用
        if (isWhiteListed(path)) {
            ServerHttpRequest marked = exchange.getRequest().mutate()
                    .headers(headers -> {
                        stripIdentityHeaders(headers);
                        headers.set(INTERNAL_HEADER, resolveInternalToken());
                    })
                    .build();
            return chain.filter(exchange.mutate().request(marked).build());
        }

        // 无令牌或令牌无效：返回 401，请求到此终止不再进入过滤器链
        String token = extractToken(exchange.getRequest());
        // 只解析一次：后续身份/租户/授权全部从同一份 Claims 读取，避免重复验签
        Claims claims = JwtUtil.parseToken(token);
        if (claims == null) {
            log.warn("未授权访问: {}", path);
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        // 访客令牌权限收窄：只能访问聊天相关路径，越权访问返回 403
        if (JwtUtil.TYP_VISITOR.equals(JwtUtil.getTokenType(claims)) && !isVisitorAllowed(path)) {
            log.warn("访客令牌越权访问: {}", path);
            exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
            return exchange.getResponse().setComplete();
        }

        // 把令牌中的身份与授权信息解析后注入请求头，并带上内部令牌供下游验真
        Long userId = JwtUtil.getUserId(claims);
        String username = JwtUtil.getUsername(claims);
        String typ = JwtUtil.getTokenType(claims);
        String tenant = JwtUtil.getTenantCode(claims);
        List<String> roles = JwtUtil.getRoles(claims);
        List<String> permissions = JwtUtil.getPermissions(claims);
        // subject 非数字说明不是本系统签发的身份，按未授权处理
        if (userId == null) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }
        ServerHttpRequest modifiedRequest = exchange.getRequest().mutate()
                .headers(headers -> {
                    // 覆盖式写入：先清空客户端同名头，再写网关解析结果
                    stripIdentityHeaders(headers);
                    headers.set("X-User-Id", String.valueOf(userId));
                    headers.set("X-Username", username != null ? username : "");
                    headers.set("X-Token-Type", typ != null ? typ : JwtUtil.TYP_STAFF);
                    // 租户：仅绑定租户的令牌才有值；下游用 JwtContext.resolveTenantCode 取用
                    if (StringUtils.hasText(tenant)) {
                        headers.set("X-Tenant-Id", tenant);
                    }
                    // 授权：逗号分隔；空列表不写头（下游视为无权限）
                    if (!roles.isEmpty()) {
                        headers.set("X-User-Roles", String.join(",", roles));
                    }
                    if (!permissions.isEmpty()) {
                        headers.set("X-User-Perms", String.join(",", permissions));
                    }
                    headers.set(INTERNAL_HEADER, resolveInternalToken());
                })
                .build();

        return chain.filter(exchange.mutate().request(modifiedRequest).build());
    }

    /**
     * 过滤器顺序：-100，在请求 ID 生成之后、日志记录之前（日志过滤器为 -200，实际先于本过滤器执行）
     */
    @Override
    public int getOrder() {
        return -100;
    }

    /** 清空客户端自带的身份类请求头（这些头只允许网关写入） */
    private static void stripIdentityHeaders(HttpHeaders headers) {
        IDENTITY_HEADERS.forEach(headers::remove);
    }

    /** 判断路径是否命中白名单（按路径段前缀匹配，避免 /auth/login-evil 之类误命中） */
    private boolean isWhiteListed(String path) {
        return matchesAnyPrefix(path, WHITE_LIST);
    }

    /** 判断路径是否为访客可访问路径（按路径段前缀匹配） */
    private boolean isVisitorAllowed(String path) {
        return matchesAnyPrefix(path, VISITOR_ALLOWED);
    }

    /** 路径段级前缀匹配：/auth/login 命中 /auth/login 与 /auth/login/x，但不命中 /auth/login-evil */
    private static boolean matchesAnyPrefix(String path, List<String> prefixes) {
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

    /** 可疑路径：目录穿越、反斜杠、重复斜杠、编码后的点/反斜杠 */
    private static boolean isSuspiciousPath(String path) {
        if (path == null || path.isEmpty() || path.indexOf('\0') >= 0) {
            return true;
        }
        String lower = path.toLowerCase(java.util.Locale.ROOT);
        return lower.contains("..") || lower.contains("\\") || lower.contains("//")
                || lower.contains("%2e") || lower.contains("%5c");
    }

    /**
     * 提取令牌：优先取 Authorization: Bearer 头，其次取查询参数 token（兼容 WebSocket 握手等无法带头的场景）
     */
    private String extractToken(ServerHttpRequest request) {
        String bearerToken = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        // 仅 WebSocket 握手允许 query 传令牌（浏览器 API 无法设置请求头）；
        // 普通 HTTP 接口不接受 URL 中的 token，避免落入访问日志/Referer。
        if (request.getPath().value().startsWith("/ws/")) {
            return request.getQueryParams().getFirst("token");
        }
        return null;
    }
}
