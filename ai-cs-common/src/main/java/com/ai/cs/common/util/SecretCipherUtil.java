package com.ai.cs.common.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 敏感字段(AES-GCM)加解密工具，用于模型 apiKey/apiSecret 落库前加密。
 *
 * <p>密钥解析优先级：环境变量 {@code AI_MODEL_SECRET} → 系统属性 {@code ai.model.secret}
 * → 内置默认密钥(仅演示/本机)。生产务必通过环境变量注入独立密钥。</p>
 *
 * <p>密文统一以 {@code enc:} 前缀标识(Base64(AES-GCM(iv+密文)))。具备幂等性：已加密输入原样返回、
 * 无法解密的输入视为明文原样返回 —— 兼容历史明文存量数据，无一次性迁移成本。</p>
 *
 * @author ai-cs
 */
@Slf4j
public final class SecretCipherUtil {

    /** 密文前缀标识：用于区分密文与历史明文存量数据 */
    public static final String PREFIX = "enc:";

    /** GCM 认证标签长度(位)：128 是推荐值 */
    private static final int GCM_TAG_BITS = 128;
    /** GCM 初始向量长度(字节)：12 是推荐值 */
    private static final int IV_BYTES = 12;
    private static final String TRANSFORM = "AES/GCM/NoPadding";

    /** 内置默认密钥：仅为本机/演示兜底，生产必须通过 AI_MODEL_SECRET 覆盖 */
    private static final String DEFAULT_SECRET = "AiCsModelSecretKey_2026_Demo_Only_ChangeMe!";

    /** 工具类禁止实例化 */
    private SecretCipherUtil() {
    }

    /**
     * 加密明文。空串/已是密文/加密失败均安全返回。
     */
    public static String encrypt(String plain) {
        if (!StringUtils.hasText(plain) || plain.startsWith(PREFIX)) {
            return plain;
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORM);
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] cipherBytes = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] merged = new byte[iv.length + cipherBytes.length];
            System.arraycopy(iv, 0, merged, 0, iv.length);
            System.arraycopy(cipherBytes, 0, merged, iv.length, cipherBytes.length);
            return PREFIX + Base64.getEncoder().encodeToString(merged);
        } catch (Exception e) {
            log.warn("字段加密失败，原样存储: {}", e.getMessage());
            return plain;
        }
    }

    /**
     * 解密密文。明文/空串/解密失败(含密钥变更)均原样返回，保证读取不中断。
     */
    public static String decrypt(String cipherText) {
        if (!StringUtils.hasText(cipherText) || !cipherText.startsWith(PREFIX)) {
            return cipherText;
        }
        try {
            byte[] merged = Base64.getDecoder().decode(cipherText.substring(PREFIX.length()));
            byte[] iv = new byte[IV_BYTES];
            System.arraycopy(merged, 0, iv, 0, IV_BYTES);
            Cipher cipher = Cipher.getInstance(TRANSFORM);
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] plainBytes = cipher.doFinal(merged, IV_BYTES, merged.length - IV_BYTES);
            return new String(plainBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("字段解密失败，按明文透传: {}", e.getMessage());
            return cipherText;
        }
    }

    /** 构造 AES-256 密钥：非 32 字节时补齐/截断，保证任意长度密钥串都可用 */
    private static SecretKeySpec key() {
        String secret = resolveSecret();
        byte[] raw = secret.getBytes(StandardCharsets.UTF_8);
        byte[] keyBytes = raw.length == 32 ? raw : padOrSlice(raw, 32);
        return new SecretKeySpec(keyBytes, "AES");
    }

    /** 字节数组定长化：超长截断、不足右侧补零 */
    private static byte[] padOrSlice(byte[] src, int len) {
        byte[] out = new byte[len];
        if (src.length >= len) {
            System.arraycopy(src, 0, out, 0, len);
        } else {
            System.arraycopy(src, 0, out, 0, src.length);
        }
        return out;
    }

    /** 解析密钥：环境变量 AI_MODEL_SECRET → 系统属性 ai.model.secret → 内置默认 */
    private static String resolveSecret() {
        String env = System.getenv("AI_MODEL_SECRET");
        if (StringUtils.hasText(env)) {
            return env.trim();
        }
        String prop = System.getProperty("ai.model.secret");
        if (StringUtils.hasText(prop)) {
            return prop.trim();
        }
        return DEFAULT_SECRET;
    }
}
