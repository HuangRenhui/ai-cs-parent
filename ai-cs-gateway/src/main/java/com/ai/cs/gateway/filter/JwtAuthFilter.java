package com.ai.cs.gateway.filter;

import com.ai.cs.common.util.JwtUtil;
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
    private static final String DEFAULT_INTERNAL = "AiCsInternalToken2026LocalOnly";

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

    /**
     * JWT 全局鉴权：白名单放行（仍注入内部令牌）→ 校验令牌 → 访客路径限制 → 注入用户身份与内部令牌
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

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
        if (token == null || !JwtUtil.validateToken(token)) {
            log.warn("未授权访问: {}", path);
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        // 访客令牌权限收窄：只能访问聊天相关路径，越权访问返回 403
        if (JwtUtil.isVisitor(token) && !isVisitorAllowed(path)) {
            log.warn("访客令牌越权访问: {}", path);
            exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
            return exchange.getResponse().setComplete();
        }

        // 把令牌中的身份与授权信息解析后注入请求头，并带上内部令牌供下游验真
        Long userId = JwtUtil.getUserId(token);
        String username = JwtUtil.getUsername(token);
        String typ = JwtUtil.getTokenType(token);
        String tenant = JwtUtil.getTenantCode(token);
        List<String> roles = JwtUtil.getRoles(token);
        List<String> permissions = JwtUtil.getPermissions(token);
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

    /** 判断路径是否命中白名单（前缀匹配） */
    private boolean isWhiteListed(String path) {
        return WHITE_LIST.stream().anyMatch(path::startsWith);
    }

    /** 判断路径是否为访客可访问路径（前缀匹配） */
    private boolean isVisitorAllowed(String path) {
        return VISITOR_ALLOWED.stream().anyMatch(path::startsWith);
    }

    /**
     * 提取令牌：优先取 Authorization: Bearer 头，其次取查询参数 token（兼容 WebSocket 握手等无法带头的场景）
     */
    private String extractToken(ServerHttpRequest request) {
        String bearerToken = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return request.getQueryParams().getFirst("token");
    }
}
