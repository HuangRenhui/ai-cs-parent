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
            "/actuator/health"
    );

    /** 访客令牌允许访问的路径（前缀匹配）：只开放聊天与会话相关接口 */
    private static final List<String> VISITOR_ALLOWED = List.of(
            "/ai/chat",
            "/session/ensure",
            "/session/message"
    );

    private static final String INTERNAL_HEADER = "X-Internal-Token";
    private static final String DEFAULT_INTERNAL = "AiCsInternalToken2026LocalOnly";

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

        // 白名单直接放行，但仍注入内部令牌，便于下游识别来自网关而不是直连伪造
        if (isWhiteListed(path)) {
            ServerHttpRequest marked = exchange.getRequest().mutate()
                    .header(INTERNAL_HEADER, resolveInternalToken())
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

        // 把令牌中的用户身份解析后注入请求头，并带上内部令牌供下游验真
        Long userId = JwtUtil.getUserId(token);
        String username = JwtUtil.getUsername(token);
        String typ = JwtUtil.getTokenType(token);
        ServerHttpRequest modifiedRequest = exchange.getRequest().mutate()
                .header("X-User-Id", String.valueOf(userId))
                .header("X-Username", username != null ? username : "")
                .header("X-Token-Type", typ != null ? typ : JwtUtil.TYP_STAFF)
                .header(INTERNAL_HEADER, resolveInternalToken())
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
