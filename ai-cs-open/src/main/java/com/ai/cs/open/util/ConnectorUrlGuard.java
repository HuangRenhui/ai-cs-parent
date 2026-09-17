package com.ai.cs.open.util;

import com.ai.cs.common.exception.BusinessException;
import com.ai.cs.open.entity.ConnectorUrlWhitelist;
import com.ai.cs.open.mapper.ConnectorUrlWhitelistMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import jakarta.annotation.Resource;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * REST 连接器出站 URL 校验，防止 SSRF。
 * 以 Spring Bean 注入白名单 Mapper（禁止 static @Resource，否则永远不生效）。
 */
@Component
public class ConnectorUrlGuard {

    /** 内置主机黑名单：本机回环地址与各云厂商的元数据服务地址 */
    private static final Set<String> BLOCKED_HOSTS = Set.of(
            "localhost", "127.0.0.1", "0.0.0.0", "::1",
            "169.254.169.254", "metadata.google.internal"
    );

    private static volatile ConnectorUrlWhitelistMapper whitelistMapper;

    /** 由 Spring 注入 Mapper 到静态字段，供静态 assertSafe 使用 */
    @Resource
    public void setWhitelistMapper(ConnectorUrlWhitelistMapper mapper) {
        ConnectorUrlGuard.whitelistMapper = mapper;
    }

    /**
     * 校验连接器出站地址是否安全，不安全时直接抛业务异常。
     *
     * @param url 待校验的连接器地址
     */
    public static void assertSafe(String url) {
        assertSafe(url, null);
    }

    /**
     * 校验连接器出站地址是否安全，支持租户白名单。
     * 依次检查：非空 → 格式合法 → 仅 http/https → 主机不在黑名单 → 租户白名单 → DNS 不指向内网。
     *
     * @param url        待校验的连接器地址
     * @param tenantCode 租户编码，用于检查租户白名单
     */
    public static void assertSafe(String url, String tenantCode) {
        if (!StringUtils.hasText(url)) {
            throw new BusinessException("连接器地址不能为空");
        }
        URI uri;
        try {
            uri = URI.create(url.trim());
        } catch (Exception e) {
            throw new BusinessException("连接器地址非法");
        }
        // 只允许 http/https 协议，拦截 file://、gopher:// 等危险协议
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw new BusinessException("连接器只允许 http/https");
        }
        // 主机名黑名单：本机、回环、云元数据及 *.internal 内网域名
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if (host.isBlank() || BLOCKED_HOSTS.contains(host) || host.endsWith(".internal")) {
            throw new BusinessException("连接器地址不允许指向内网或元数据地址");
        }
        // 租户白名单检查：配置了白名单则必须命中其一
        if (StringUtils.hasText(tenantCode) && whitelistMapper != null) {
            checkTenantWhitelist(tenantCode, host);
        }
        try {
            // DNS 解析后逐 IP 检查：防止用公网域名绕过黑名单、实际解析到内网 IP
            for (InetAddress addr : InetAddress.getAllByName(host)) {
                if (addr.isAnyLocalAddress() || addr.isLoopbackAddress() || addr.isLinkLocalAddress()
                        || addr.isSiteLocalAddress() || addr.isMulticastAddress()) {
                    throw new BusinessException("连接器地址不允许指向内网或元数据地址");
                }
            }
        } catch (UnknownHostException e) {
            throw new BusinessException("连接器地址无法解析");
        }
    }

    /**
     * 检查租户白名单。
     * 如果租户配置了白名单，则目标域名必须匹配白名单中的某个模式。
     */
    private static void checkTenantWhitelist(String tenantCode, String host) {
        List<ConnectorUrlWhitelist> whitelist = whitelistMapper.selectList(
                new LambdaQueryWrapper<ConnectorUrlWhitelist>()
                        .eq(ConnectorUrlWhitelist::getTenantCode, tenantCode)
        );
        if (whitelist.isEmpty()) {
            // 租户未配置白名单，允许所有通过黑名单检查的地址
            return;
        }
        for (ConnectorUrlWhitelist entry : whitelist) {
            String pattern = entry.getDomainPattern();
            if (StringUtils.hasText(pattern) && matchesPattern(host, pattern)) {
                return;
            }
        }
        throw new BusinessException("连接器地址不在租户白名单中");
    }

    /**
     * 检查域名是否匹配白名单模式。
     * 支持通配符 *.example.com。
     */
    private static boolean matchesPattern(String host, String pattern) {
        if (pattern.startsWith("*.")) {
            String suffix = pattern.substring(2);
            return host.equals(suffix) || host.endsWith("." + suffix);
        }
        return host.equals(pattern);
    }
}
