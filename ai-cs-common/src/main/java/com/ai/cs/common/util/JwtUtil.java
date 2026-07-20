package com.ai.cs.common.util;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

/**
 * JWT工具类
 * 用于生成和解析JWT Token
 *
 * @author huangrenhui
 * @date 2026/7/20
 */
@Slf4j
public class JwtUtil {

    /** 默认密钥（生产环境应从配置读取） */
    private static final String DEFAULT_SECRET = "AiCsSystemJwtSecretKey2026ForTokenGenerationAndValidation";

    /** Token有效期，默认24小时 */
    private static final long DEFAULT_EXPIRE = 24 * 60 * 60 * 1000L;

    /** Bearer前缀 */
    private static final String TOKEN_PREFIX = "Bearer ";

    private static SecretKey getKey(String secret) {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            byte[] paddedKey = new byte[32];
            System.arraycopy(keyBytes, 0, paddedKey, 0, keyBytes.length);
            keyBytes = paddedKey;
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * 生成JWT Token
     */
    public static String generateToken(Long userId, String username) {
        return generateToken(userId, username, DEFAULT_EXPIRE);
    }

    /**
     * 生成JWT Token（指定过期时间）
     */
    public static String generateToken(Long userId, String username, long expireMs) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + expireMs);
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .issuedAt(now)
                .expiration(expiration)
                .signWith(getKey(DEFAULT_SECRET))
                .compact();
    }

    /**
     * 生成JWT Token（带自定义Claims）
     */
    public static String generateToken(Long userId, String username, Map<String, Object> claims) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + DEFAULT_EXPIRE);
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .claims(claims)
                .issuedAt(now)
                .expiration(expiration)
                .signWith(getKey(DEFAULT_SECRET))
                .compact();
    }

    /**
     * 解析JWT Token
     */
    public static Claims parseToken(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        if (token.startsWith(TOKEN_PREFIX)) {
            token = token.substring(TOKEN_PREFIX.length());
        }
        try {
            return Jwts.parser()
                    .verifyWith(getKey(DEFAULT_SECRET))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            log.warn("JWT Token已过期: {}", e.getMessage());
            return null;
        } catch (JwtException e) {
            log.warn("JWT Token解析失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 从Token中获取用户ID
     */
    public static Long getUserId(String token) {
        Claims claims = parseToken(token);
        if (claims == null) {
            return null;
        }
        return Long.parseLong(claims.getSubject());
    }

    /**
     * 从Token中获取用户名
     */
    public static String getUsername(String token) {
        Claims claims = parseToken(token);
        if (claims == null) {
            return null;
        }
        return claims.get("username", String.class);
    }

    /**
     * 验证Token是否有效
     */
    public static boolean validateToken(String token) {
        return parseToken(token) != null;
    }

    /**
     * 获取Token过期时间
     */
    public static Date getExpiration(String token) {
        Claims claims = parseToken(token);
        return claims != null ? claims.getExpiration() : null;
    }
}
