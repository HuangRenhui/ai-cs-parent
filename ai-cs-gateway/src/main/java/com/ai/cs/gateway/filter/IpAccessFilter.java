package com.ai.cs.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * IP 白名单/黑名单过滤器
 * 支持通过环境变量配置白名单和黑名单，增强网络安全
 *
 * @author huangrenhui
 * @date 2026/9/11
 */
@Slf4j
@Component
public class IpAccessFilter implements GlobalFilter, Ordered {

    /** IP 白名单（逗号分隔），配置项 IP_WHITELIST，未配置时不启用白名单 */
    @Value("${security.ip.whitelist:}")
    private String whitelistConfig;

    /** IP 黑名单（逗号分隔），配置项 IP_BLACKLIST，未配置时不启用黑名单 */
    @Value("${security.ip.blacklist:}")
    private String blacklistConfig;

    /** 是否启用 IP 白名单模式 */
    @Value("${security.ip.whitelist.enabled:false}")
    private boolean whitelistEnabled;

    /** 是否启用 IP 黑名单模式 */
    @Value("${security.ip.blacklist.enabled:false}")
    private boolean blacklistEnabled;

    /**
     * IP 访问控制：黑名单优先 → 白名单（启用时）→ 放行
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String clientIp = getClientIp(exchange.getRequest());

        // 黑名单检查
        if (blacklistEnabled && isIpInBlacklist(clientIp)) {
            log.warn("IP 在黑名单中，拒绝访问: {}", clientIp);
            exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
            return exchange.getResponse().setComplete();
        }

        // 白名单检查（启用时）
        if (whitelistEnabled && !isIpInWhitelist(clientIp)) {
            log.warn("IP 不在白名单中，拒绝访问: {}", clientIp);
            exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
            return exchange.getResponse().setComplete();
        }

        return chain.filter(exchange);
    }

    /** 过滤器顺序：在 JWT 认证之前执行（-200） */
    @Override
    public int getOrder() {
        return -200;
    }

    /** 获取客户端真实 IP */
    private String getClientIp(ServerHttpRequest request) {
        // 优先从 X-Forwarded-For 获取（经过代理时）
        String xff = request.getHeaders().getFirst("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            // 取第一个 IP（可能有多个代理）
            return xff.split(",")[0].trim();
        }

        // 其次从 X-Real-IP 获取
        String xri = request.getHeaders().getFirst("X-Real-IP");
        if (xri != null && !xri.isBlank()) {
            return xri.trim();
        }

        // 最后从 RemoteAddress 获取
        return request.getRemoteAddress() != null
                ? request.getRemoteAddress().getAddress().getHostAddress()
                : "unknown";
    }

    /** 检查 IP 是否在黑名单中 */
    private boolean isIpInBlacklist(String ip) {
        Set<String> blacklist = parseIpList(blacklistConfig);
        return isIpMatch(ip, blacklist);
    }

    /** 检查 IP 是否在白名单中 */
    private boolean isIpInWhitelist(String ip) {
        Set<String> whitelist = parseIpList(whitelistConfig);
        return isIpMatch(ip, whitelist);
    }

    /** 解析 IP 列表（支持单个 IP、CIDR、通配符） */
    private Set<String> parseIpList(String config) {
        if (config == null || config.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(config.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toSet());
    }

    /** IP 匹配检查（支持精确匹配、CIDR、通配符） */
    private boolean isIpMatch(String ip, Set<String> ipList) {
        if (ipList.isEmpty()) {
            return false;
        }

        for (String pattern : ipList) {
            if (pattern.contains("/")) {
                // CIDR 匹配
                if (isCidrMatch(ip, pattern)) {
                    return true;
                }
            } else if (pattern.contains("*")) {
                // 通配符匹配
                if (isWildcardMatch(ip, pattern)) {
                    return true;
                }
            } else {
                // 精确匹配
                if (ip.equals(pattern)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** CIDR 匹配（简化版，仅支持 /24、/16、/8） */
    private boolean isCidrMatch(String ip, String cidr) {
        try {
            String[] parts = cidr.split("/");
            String network = parts[0];
            int prefix = Integer.parseInt(parts[1]);

            String[] ipParts = ip.split("\\.");
            String[] networkParts = network.split("\\.");

            if (prefix >= 8) {
                if (!ipParts[0].equals(networkParts[0])) return false;
            }
            if (prefix >= 16) {
                if (!ipParts[1].equals(networkParts[1])) return false;
            }
            if (prefix >= 24) {
                if (!ipParts[2].equals(networkParts[2])) return false;
            }
            if (prefix >= 32) {
                if (!ipParts[3].equals(networkParts[3])) return false;
            }

            return true;
        } catch (Exception e) {
            log.warn("CIDR 解析失败: {}", cidr);
            return false;
        }
    }

    /** 通配符匹配（如 192.168.*.*） */
    private boolean isWildcardMatch(String ip, String pattern) {
        String[] ipParts = ip.split("\\.");
        String[] patternParts = pattern.split("\\.");

        for (int i = 0; i < 4; i++) {
            String patternPart = i < patternParts.length ? patternParts[i] : "*";
            if (!patternPart.equals("*") && !patternPart.equals(ipParts[i])) {
                return false;
            }
        }
        return true;
    }
}
