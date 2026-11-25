package com.ai.cs.gateway.config;

import com.ai.cs.common.security.SecretPolicy;
import com.ai.cs.common.util.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import jakarta.annotation.PostConstruct;

/**
 * 网关不扫描 common 组件，在此单独把密钥灌进 {@link JwtUtil}。
 */
@Slf4j
@Component
public class GatewayJwtConfig {

    /** JWT 签名密钥（配置项 ai.jwt.secret，缺省时回退环境变量 JWT_SECRET） */
    @Value("${ai.jwt.secret:}")
    private String secret;

    /** 员工令牌有效期（毫秒），默认 24 小时 */
    @Value("${ai.jwt.expire-ms:86400000}")
    private long expireMs;

    /** 访客令牌有效期（毫秒），默认 2 小时 */
    @Value("${ai.jwt.visitor-expire-ms:7200000}")
    private long visitorExpireMs;

    /** 当前激活的 Spring 配置，用于拒绝在生产/未指定 profile 时使用默认密钥 */
    @Value("${spring.profiles.active:}")
    private String activeProfile;

    /**
     * 启动时把密钥与有效期注入 {@link JwtUtil}（静态工具类无法走 Spring 注入，故在此显式配置）。
     *
     * <p>网关不扫描 common 组件，因此这里必须与 {@code JwtSettings} 保持同一套强校验：
     * 生产、预发与空 profile 未配置密钥时直接拒绝启动，避免用仓库公开的默认串验签。</p>
     */
    @PostConstruct
    public void apply() {
        // 配置项优先，环境变量兜底
        String resolved = StringUtils.hasText(secret) ? secret : System.getenv("JWT_SECRET");
        boolean lenient = SecretPolicy.isLenient(activeProfile);
        JwtUtil.configure(resolved, expireMs, visitorExpireMs, lenient);
        if (!StringUtils.hasText(resolved)) {
            if (!lenient) {
                throw new IllegalStateException(
                        "网关未配置 JWT_SECRET / ai.jwt.secret（当前 profile=" + activeProfile
                                + "）。请显式注入密钥，或本地开发使用 dev/local profile。");
            }
            log.warn("网关未配置 JWT_SECRET / ai.jwt.secret，dev/local 环境使用内置演示密钥。");
            return;
        }
        if (!lenient && JwtUtil.isInsecureSecret(resolved)) {
            throw new IllegalStateException("禁止在生产/未指定 profile 的环境使用仓库内置的默认 JWT 密钥");
        }
        if (!lenient && resolved.trim().getBytes(java.nio.charset.StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("JWT_SECRET 过短，生产环境要求至少 32 字节");
        }
    }
}
