package com.ai.cs.common.web;

import com.ai.cs.common.event.AuditEventPublisher;
import com.ai.cs.common.security.JwtContext;
import com.alibaba.fastjson2.JSONObject;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

/**
 * 管理员写操作审计：记录操作人、可读动作、路径与脱敏参数，异步入 Redis 再落库。
 */
@Slf4j
@Component
@Order(50)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class AuditLogFilter extends OncePerRequestFilter {

    private static final int CACHE_LIMIT = 4096;

    private final AuditEventPublisher publisher;

    public AuditLogFilter(AuditEventPublisher publisher) {
        this.publisher = publisher;
    }

    /**
     * 仅对管理员后台变更类请求记审计；查询、聊天、内部探活不进队列。
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String method = request.getMethod();
        String uri = request.getRequestURI();
        if (!AuditOperationSupport.shouldAudit(method, uri)) {
            filterChain.doFilter(request, response);
            return;
        }
        ContentCachingRequestWrapper wrapped = request instanceof ContentCachingRequestWrapper caching
                ? caching
                : new ContentCachingRequestWrapper(request, CACHE_LIMIT);
        long start = System.currentTimeMillis();
        try {
            filterChain.doFilter(wrapped, response);
        } finally {
            publish(wrapped, response, start);
        }
    }

    /** 组装审计事件。登录尚未写入 JWT 时，从脱敏后的请求体取用户名。 */
    private void publish(ContentCachingRequestWrapper request, HttpServletResponse response, long start) {
        String uri = request.getRequestURI();
        String method = request.getMethod();
        String params = readParams(request);
        Long userId = JwtContext.getCurrentUserId();
        String username = JwtContext.getCurrentUsername();
        if (username == null || username.isBlank()) {
            username = AuditOperationSupport.usernameFromParams(params);
        }
        boolean adminLogin = uri != null && uri.contains("/auth/login") && !uri.contains("platform");
        // 无操作人的内部 Feign 调用不记，避免审计里全是空用户
        if ((userId == null && (username == null || username.isBlank())) && !adminLogin) {
            return;
        }
        JSONObject event = new JSONObject();
        event.put("userId", userId);
        event.put("username", username);
        event.put("module", AuditOperationSupport.moduleOf(uri));
        event.put("operation", AuditOperationSupport.operationOf(method, uri));
        event.put("method", "");
        event.put("requestUrl", uri);
        event.put("requestMethod", method);
        event.put("requestParams", params);
        event.put("ip", clientIp(request));
        event.put("duration", System.currentTimeMillis() - start);
        event.put("status", response.getStatus() < 400 ? 1 : 0);
        event.put("errorMsg", null);
        event.put("createTime", LocalDateTime.now().toString());
        publisher.publish(event);
    }

    /** 读缓存的请求体并脱敏；非 JSON 的表单只记 query */
    private static String readParams(ContentCachingRequestWrapper request) {
        String query = request.getQueryString();
        String body = "";
        byte[] buf = request.getContentAsByteArray();
        if (buf != null && buf.length > 0 && isTextBody(request.getContentType())) {
            body = new String(buf, StandardCharsets.UTF_8);
        }
        if (body.isBlank()) {
            return AuditOperationSupport.redactParams(query);
        }
        if (query == null || query.isBlank()) {
            return AuditOperationSupport.redactParams(body);
        }
        return AuditOperationSupport.redactParams(body);
    }

    private static boolean isTextBody(String contentType) {
        if (contentType == null) {
            return true;
        }
        String ct = contentType.toLowerCase();
        return ct.contains(MediaType.APPLICATION_JSON_VALUE)
                || ct.contains(MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                || ct.contains("text/");
    }

    /** 优先取反向代理头，其次 remoteAddr */
    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
