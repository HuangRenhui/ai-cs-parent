package com.ai.cs.common.security;

import com.ai.cs.common.util.JwtUtil;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import jakarta.annotation.PostConstruct;

/**
 * 从 {@code ai.jwt} / {@code JWT_SECRET} 注入签名密钥。网关单独有一份同名配置。
 */
@Data
@Slf4j
@Component
@ConfigurationProperties(prefix = "ai.jwt")
public class JwtSettings {

    /** 签名密钥，空则回退环境变量 JWT_SECRET；仅 dev/local/test 允许再回退内置演示密钥 */
    private String secret = "";
    /** 员工(管理端) token 有效期，默认 24 小时 */
    private long expireMs = 24 * 60 * 60 * 1000L;
    /** 访客(C 端聊窗) token 有效期，默认 2 小时 */
    private long visitorExpireMs = 2 * 60 * 60 * 1000L;

    /** 当前激活的 Spring 配置，用于在非 dev/local/test 环境拒绝默认 JWT 密钥 */
    @org.springframework.beans.factory.annotation.Value("${spring.profiles.active:}")
    private String activeProfile;

    /**
     * 启动时把配置注入 JwtUtil 静态上下文。
     *
     * <p>只有 dev/local/test 允许使用内置演示密钥；生产、预发以及未显式指定 profile 的场景
     * 一律要求显式配置，否则直接拒绝启动。</p>
     */
    @PostConstruct
    public void apply() {
        String resolved = StringUtils.hasText(secret) ? secret : System.getenv("JWT_SECRET");
        boolean lenient = SecretPolicy.isLenient(activeProfile);
        JwtUtil.configure(resolved, expireMs, visitorExpireMs, lenient);
        if (!StringUtils.hasText(resolved)) {
            if (!lenient) {
                throw new IllegalStateException(
                        "未配置 JWT_SECRET / ai.jwt.secret（当前 profile=" + activeProfile
                                + "）。请显式注入密钥，或本地开发使用 dev/local profile。");
            }
            log.warn("未配置 JWT_SECRET / ai.jwt.secret，dev/local 环境使用内置演示密钥。");
            return;
        }
        if (!lenient && JwtUtil.isInsecureSecret(resolved)) {
            throw new IllegalStateException("禁止在生产/未指定 profile 的环境使用仓库内置的默认 JWT 密钥");
        }
        // HS256 要求密钥至少 32 字节：短密钥可被暴力枚举，严格模式下直接拒绝启动
        if (!lenient && resolved.trim().getBytes(java.nio.charset.StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("JWT_SECRET 过短，生产环境要求至少 32 字节");
        }
    }
}
