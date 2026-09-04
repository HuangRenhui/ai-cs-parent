package com.ai.cs.common.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

/**
 * JWT 工具。密钥优先读 {@code JWT_SECRET} / {@link #configure}，未配置时用内置默认值（仅本机）。
 */
@Slf4j
public class JwtUtil {

    public static final String CLAIM_TYP = "typ";
    public static final String TYP_STAFF = "staff";
    public static final String TYP_VISITOR = "visitor";

    static final String DEFAULT_SECRET = "AiCsSystemJwtSecretKey2026ForTokenGenerationAndValidation";

    private static final long DEFAULT_EXPIRE = 24 * 60 * 60 * 1000L;
    private static final long DEFAULT_VISITOR_EXPIRE = 2 * 60 * 60 * 1000L;
    private static final String TOKEN_PREFIX = "Bearer ";

    private static volatile String configuredSecret;
    private static volatile long staffExpireMs = DEFAULT_EXPIRE;
    private static volatile long visitorExpireMs = DEFAULT_VISITOR_EXPIRE;

    private JwtUtil() {
    }

    public static void configure(String secret, Long staffExpire, Long visitorExpire) {
        if (StringUtils.hasText(secret)) {
            configuredSecret = secret.trim();
        }
        if (staffExpire != null && staffExpire > 0) {
            staffExpireMs = staffExpire;
        }
        if (visitorExpire != null && visitorExpire > 0) {
            visitorExpireMs = visitorExpire;
        }
    }

    static String resolveSecret() {
        if (StringUtils.hasText(configuredSecret)) {
            return configuredSecret;
        }
        String env = System.getenv("JWT_SECRET");
        if (StringUtils.hasText(env)) {
            return env.trim();
        }
        String prop = System.getProperty("ai.jwt.secret");
        if (StringUtils.hasText(prop)) {
            return prop.trim();
        }
        return DEFAULT_SECRET;
    }

    private static SecretKey getKey() {
        byte[] keyBytes = resolveSecret().getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            byte[] paddedKey = new byte[32];
            System.arraycopy(keyBytes, 0, paddedKey, 0, keyBytes.length);
            keyBytes = paddedKey;
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public static String generateToken(Long userId, String username) {
        return generateToken(userId, username, staffExpireMs);
    }

    public static String generateToken(Long userId, String username, long expireMs) {
        return buildToken(userId, username, TYP_STAFF, expireMs, null);
    }

    public static String generateToken(Long userId, String username, Map<String, Object> claims) {
        return buildToken(userId, username, TYP_STAFF, staffExpireMs, claims);
    }

    public static String generateVisitorToken(Long userId, String username) {
        return buildToken(userId, username, TYP_VISITOR, visitorExpireMs, null);
    }

    private static String buildToken(Long userId, String username, String typ, long expireMs,
                                     Map<String, Object> extraClaims) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + expireMs);
        var builder = Jwts.builder()
                .subject(String.valueOf(userId == null ? 0L : userId))
                .claim("username", username)
                .claim(CLAIM_TYP, typ)
                .issuedAt(now)
                .expiration(expiration);
        if (extraClaims != null) {
            extraClaims.forEach((key, value) -> {
                if (!"username".equals(key) && !CLAIM_TYP.equals(key) && !"sub".equals(key)) {
                    builder.claim(key, value);
                }
            });
        }
        return builder.signWith(getKey()).compact();
    }

    public static Claims parseToken(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        if (token.startsWith(TOKEN_PREFIX)) {
            token = token.substring(TOKEN_PREFIX.length());
        }
        try {
            return Jwts.parser()
                    .verifyWith(getKey())
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

    public static Long getUserId(String token) {
        Claims claims = parseToken(token);
        if (claims == null || claims.getSubject() == null) {
            return null;
        }
        return Long.parseLong(claims.getSubject());
    }

    public static String getUsername(String token) {
        Claims claims = parseToken(token);
        if (claims == null) {
            return null;
        }
        return claims.get("username", String.class);
    }

    public static String getTokenType(String token) {
        Claims claims = parseToken(token);
        if (claims == null) {
            return null;
        }
        String typ = claims.get(CLAIM_TYP, String.class);
        return StringUtils.hasText(typ) ? typ : TYP_STAFF;
    }

    public static boolean isVisitor(String token) {
        return TYP_VISITOR.equals(getTokenType(token));
    }

    public static boolean validateToken(String token) {
        return parseToken(token) != null;
    }

    public static Date getExpiration(String token) {
        Claims claims = parseToken(token);
        return claims != null ? claims.getExpiration() : null;
    }
}
