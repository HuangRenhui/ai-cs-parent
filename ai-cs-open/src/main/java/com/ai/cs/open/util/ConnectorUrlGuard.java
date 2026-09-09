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

    /** 内置主机黑名单：本机回环地址与各云厂商的元数据服务地址 */
    private static final Set<String> BLOCKED_HOSTS = Set.of(
            "localhost", "127.0.0.1", "0.0.0.0", "::1",
            "169.254.169.254", "metadata.google.internal"
    );

    /** 工具类禁止实例化 */
    private ConnectorUrlGuard() {
    }

    /**
     * 校验连接器出站地址是否安全，不安全时直接抛业务异常。
     * 依次检查：非空 → 格式合法 → 仅 http/https → 主机不在黑名单 → DNS 解析结果不指向内网，
     * 防止攻击者借助连接器让服务端请求内网或云元数据接口（SSRF）。
     *
     * @param url 待校验的连接器地址
     */
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
}
