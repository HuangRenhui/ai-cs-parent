package com.ai.cs.open.util;

import com.ai.cs.common.exception.BusinessException;
import org.springframework.util.StringUtils;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Set;

/**
 * REST 连接器出站 URL 校验，防止 SSRF。
 */
public final class ConnectorUrlGuard {

    private static final Set<String> BLOCKED_HOSTS = Set.of(
            "localhost", "127.0.0.1", "0.0.0.0", "::1",
            "169.254.169.254", "metadata.google.internal"
    );

    private ConnectorUrlGuard() {
    }

    public static void assertSafe(String url) {
        if (!StringUtils.hasText(url)) {
            throw new BusinessException("连接器地址不能为空");
        }
        URI uri;
        try {
            uri = URI.create(url.trim());
        } catch (Exception e) {
            throw new BusinessException("连接器地址非法");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw new BusinessException("连接器只允许 http/https");
        }
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if (host.isBlank() || BLOCKED_HOSTS.contains(host) || host.endsWith(".internal")) {
            throw new BusinessException("连接器地址不允许指向内网或元数据地址");
        }
        try {
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
}
