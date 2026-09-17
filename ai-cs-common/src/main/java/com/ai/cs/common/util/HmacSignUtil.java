package com.ai.cs.common.util;

import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * HMAC-SHA256 签名工具，用于入站/出站 Webhook 鉴权。
 */
public final class HmacSignUtil {

    private HmacSignUtil() {
    }

    /**
     * 计算 HMAC-SHA256 十六进制签名（小写）。
     *
     * @param secret 共享密钥
     * @param body   原始报文体
     * @return hex 签名；密钥或正文为空时返回空串
     */
    public static String hmacSha256Hex(String secret, String body) {
        if (!StringUtils.hasText(secret)) {
            return "";
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] raw = mac.doFinal((body == null ? "" : body).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(raw);
        } catch (Exception e) {
            throw new IllegalStateException("HMAC 计算失败", e);
        }
    }

    /**
     * 恒定时间比较，避免签名校验时的时序攻击。
     */
    public static boolean equalsQuietly(String expected, String actual) {
        if (!StringUtils.hasText(expected) || !StringUtils.hasText(actual)) {
            return false;
        }
        byte[] a = expected.trim().toLowerCase().getBytes(StandardCharsets.UTF_8);
        byte[] b = actual.trim().toLowerCase().getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(a, b);
    }
}
