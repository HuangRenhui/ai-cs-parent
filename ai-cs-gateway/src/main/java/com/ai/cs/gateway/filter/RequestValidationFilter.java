package com.ai.cs.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 请求验证过滤器
 * 检测和阻止常见的 Web 攻击模式（SQL 注入、XSS、路径遍历等）
 *
 * @author huangrenhui
 * @date 2026/9/11
 */
@Slf4j
@Component
public class RequestValidationFilter implements GlobalFilter, Ordered {

    /** SQL 注入检测模式 */
    private static final List<Pattern> SQL_INJECTION_PATTERNS = List.of(
            Pattern.compile("(?i)(union\\s+select|select\\s+.*\\s+from|insert\\s+into|delete\\s+from|update\\s+.*\\s+set|drop\\s+table|alter\\s+table)"),
            Pattern.compile("(?i)(\\bor\\s+1\\s*=\\s*1|\\band\\s+1\\s*=\\s*1|\\bor\\s+true|\\band\\s+true)"),
            Pattern.compile("(?i)(exec\\s*\\(|execute\\s*\\(|xp_cmdshell|sp_oacreate)"),
            Pattern.compile("(?i)(--|;|\\/\\*|\\*\\/|@@|char\\(|nchar\\(|varchar\\(|cast\\(|convert\\()")
    );

    /** XSS 攻击检测模式 */
    private static final List<Pattern> XSS_PATTERNS = List.of(
            Pattern.compile("(?i)<script[^>]*>.*?</script>"),
            Pattern.compile("(?i)javascript:"),
            Pattern.compile("(?i)on\\w+\\s*=\\s*[\"']?[^\"'\\s>]+"),
            Pattern.compile("(?i)<iframe[^>]*>.*?</iframe>"),
            Pattern.compile("(?i)<object[^>]*>.*?</object>"),
            Pattern.compile("(?i)<embed[^>]*>.*?</embed>")
    );

    /** 路径遍历检测模式 */
    private static final List<Pattern> PATH_TRAVERSAL_PATTERNS = List.of(
            Pattern.compile("\\.\\./"),
            Pattern.compile("\\.\\\\"),
            Pattern.compile("%2e%2e%2f", Pattern.CASE_INSENSITIVE),
            Pattern.compile("%2e%2e%5c", Pattern.CASE_INSENSITIVE),
            Pattern.compile("..%2f", Pattern.CASE_INSENSITIVE),
            Pattern.compile("..%5c", Pattern.CASE_INSENSITIVE)
    );

    /** 命令注入检测模式 */
    private static final List<Pattern> COMMAND_INJECTION_PATTERNS = List.of(
            Pattern.compile("(?i)(\\|\\||&&|;|`|\\$\\(|\\$\\{)"),
            Pattern.compile("(?i)(nc\\s+|netcat\\s+|telnet\\s+|wget\\s+|curl\\s+)"),
            Pattern.compile("(?i)(/bin/sh|/bin/bash|cmd\\.exe|powershell)")
    );

    /** 白名单路径（跳过验证） */
    private static final List<String> WHITELIST_PATHS = List.of(
            "/actuator/health",
            "/files/avatars/",
            "/knowledge/health"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        String queryString = exchange.getRequest().getURI().getQuery();
        String userAgent = exchange.getRequest().getHeaders().getFirst("User-Agent");

        // 白名单路径跳过验证
        if (isWhitelisted(path)) {
            return chain.filter(exchange);
        }

        // 检查路径遍历攻击
        if (containsPattern(path, PATH_TRAVERSAL_PATTERNS)) {
            log.warn("检测到路径遍历攻击，路径：{}，User-Agent：{}", path, userAgent);
            exchange.getResponse().setStatusCode(HttpStatus.BAD_REQUEST);
            return exchange.getResponse().setComplete();
        }

        // 检查查询参数中的攻击模式
        if (queryString != null && !queryString.isBlank()) {
            // SQL 注入检测
            if (containsPattern(queryString, SQL_INJECTION_PATTERNS)) {
                log.warn("检测到 SQL 注入攻击，查询参数：{}，User-Agent：{}", queryString, userAgent);
                exchange.getResponse().setStatusCode(HttpStatus.BAD_REQUEST);
                return exchange.getResponse().setComplete();
            }

            // XSS 攻击检测
            if (containsPattern(queryString, XSS_PATTERNS)) {
                log.warn("检测到 XSS 攻击，查询参数：{}，User-Agent：{}", queryString, userAgent);
                exchange.getResponse().setStatusCode(HttpStatus.BAD_REQUEST);
                return exchange.getResponse().setComplete();
            }

            // 命令注入检测
            if (containsPattern(queryString, COMMAND_INJECTION_PATTERNS)) {
                log.warn("检测到命令注入攻击，查询参数：{}，User-Agent：{}", queryString, userAgent);
                exchange.getResponse().setStatusCode(HttpStatus.BAD_REQUEST);
                return exchange.getResponse().setComplete();
            }
        }

        // 检查 User-Agent 中的恶意模式
        if (userAgent != null && containsPattern(userAgent, COMMAND_INJECTION_PATTERNS)) {
            log.warn("检测到恶意 User-Agent：{}", userAgent);
            exchange.getResponse().setStatusCode(HttpStatus.BAD_REQUEST);
            return exchange.getResponse().setComplete();
        }

        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return -150; // 在 IP 过滤器之后，JWT 认证之前
    }

    /** 检查路径是否在白名单中 */
    private boolean isWhitelisted(String path) {
        return WHITELIST_PATHS.stream().anyMatch(path::startsWith);
    }

    /** 检查字符串是否匹配任一模式 */
    private boolean containsPattern(String input, List<Pattern> patterns) {
        for (Pattern pattern : patterns) {
            if (pattern.matcher(input).find()) {
                return true;
            }
        }
        return false;
    }
}
