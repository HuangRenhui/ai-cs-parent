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

    /** token 类型 claim 键名 */
    public static final String CLAIM_TYP = "typ";
    /** 员工(管理端) token 类型 */
    public static final String TYP_STAFF = "staff";
    /** 访客(C 端聊窗) token 类型 */
    public static final String TYP_VISITOR = "visitor";

    /** 内置默认密钥：仅为本机/演示兜底，生产必须通过 JWT_SECRET / ai.jwt.secret 覆盖 */
    static final String DEFAULT_SECRET = "AiCsSystemJwtSecretKey2026ForTokenGenerationAndValidation";

    /** 员工 token 默认有效期：24 小时 */
    private static final long DEFAULT_EXPIRE = 24 * 60 * 60 * 1000L;
    /** 访客 token 默认有效期：2 小时 */
    private static final long DEFAULT_VISITOR_EXPIRE = 2 * 60 * 60 * 1000L;
    private static final String TOKEN_PREFIX = "Bearer ";

    // 运行期由 JwtSettings 注入；volatile 保证多线程可见
    private static volatile String configuredSecret;
    private static volatile long staffExpireMs = DEFAULT_EXPIRE;
    private static volatile long visitorExpireMs = DEFAULT_VISITOR_EXPIRE;

    /** 工具类禁止实例化 */
    private JwtUtil() {
    }

    /**
     * 注入密钥与有效期（由 {@code JwtSettings} 启动时调用）。
     * 空值/非正值忽略，保留原配置。
     */
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

    /** 解析密钥：注入值 → 环境变量 JWT_SECRET → 系统属性 ai.jwt.secret → 内置默认 */
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

    /** 构造 HMAC-SHA 签名密钥；不足 32 字节时右侧补零，满足 HS256 最小密钥长度要求 */
    private static SecretKey getKey() {
        byte[] keyBytes = resolveSecret().getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            byte[] paddedKey = new byte[32];
            System.arraycopy(keyBytes, 0, paddedKey, 0, keyBytes.length);
            keyBytes = paddedKey;
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /** 生成员工 token（默认员工有效期） */
    public static String generateToken(Long userId, String username) {
        return generateToken(userId, username, staffExpireMs);
    }

    /** 生成员工 token（自定义有效期） */
    public static String generateToken(Long userId, String username, long expireMs) {
        return buildToken(userId, username, TYP_STAFF, expireMs, null);
    }

    /** 生成员工 token（附带额外 claim） */
    public static String generateToken(Long userId, String username, Map<String, Object> claims) {
        return buildToken(userId, username, TYP_STAFF, staffExpireMs, claims);
    }

    /** 生成访客 token（C 端聊窗用，有效期更短） */
    public static String generateVisitorToken(Long userId, String username) {
        return buildToken(userId, username, TYP_VISITOR, visitorExpireMs, null);
    }

    /**
     * 组装 JWT：subject=userId，附带 username 与 typ(员工/访客) 两个标准 claim。
     * 额外 claim 不允许覆盖这三个保留键，防止调用方篡改身份字段。
     */
    private static String buildToken(Long userId, String username, String typ, long expireMs,
                                     Map<String, Object> extraClaims) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + expireMs);
        // userId 为空时写 0，避免 subject 为 null 触发 jjwt 异常
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

    /**
     * 解析并校验 token。兼容带 "Bearer " 前缀的入参。
     *
     * @return 有效则返回 Claims；过期/签名错误/格式错误均返回 null（不抛异常，便于过滤器统一按未登录处理）
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

    /** 从 token 取用户ID（subject），无效返回 null */
    public static Long getUserId(String token) {
        Claims claims = parseToken(token);
        if (claims == null || claims.getSubject() == null) {
            return null;
        }
        return Long.parseLong(claims.getSubject());
    }

    /** 从 token 取用户名，无效返回 null */
    public static String getUsername(String token) {
        Claims claims = parseToken(token);
        if (claims == null) {
            return null;
        }
        return claims.get("username", String.class);
    }

    /** 从 token 取类型(staff/visitor)；旧 token 无 typ 字段时按 staff 处理 */
    public static String getTokenType(String token) {
        Claims claims = parseToken(token);
        if (claims == null) {
            return null;
        }
        String typ = claims.get(CLAIM_TYP, String.class);
        return StringUtils.hasText(typ) ? typ : TYP_STAFF;
    }

    /** 是否访客 token */
    public static boolean isVisitor(String token) {
        return TYP_VISITOR.equals(getTokenType(token));
    }

    /** token 是否有效（未过期且签名正确） */
    public static boolean validateToken(String token) {
        return parseToken(token) != null;
    }

    /** 取 token 过期时间，无效返回 null */
    public static Date getExpiration(String token) {
        Claims claims = parseToken(token);
        return claims != null ? claims.getExpiration() : null;
    }
}
