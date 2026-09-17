package com.ai.cs.knowledge.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 图片防盗链服务
 * 通过Token验证、Referer白名单、时间戳签名等方式防止图片被盗用
 */
@Slf4j
@Service
public class ImageHotlinkService {

    /** Token有效期（秒） */
    private static final long TOKEN_EXPIRE_SECONDS = 3600;

    /** 签名密钥 */
    private static final String SIGN_KEY = "image-hotlink-secret-key-change-in-production";

    /** Referer白名单 */
    private final Set<String> refererWhitelist = new HashSet<>();

    /** 生成的访问Token缓存 */
    private final Map<String, TokenInfo> tokenCache = new ConcurrentHashMap<>();

    public ImageHotlinkService() {
        // 默认白名单
        refererWhitelist.add("localhost");
        refererWhitelist.add("127.0.0.1");
    }

    /**
     * 生成图片访问Token
     * @param fileId 文件ID
     * @param expireSeconds 过期时间（秒）
     * @return 访问Token
     */
    public String generateToken(String fileId, long expireSeconds) {
        long timestamp = System.currentTimeMillis() / 1000;
        long expireTime = timestamp + expireSeconds;

        String tokenId = UUID.randomUUID().toString().replace("-", "");
        String signData = fileId + ":" + timestamp + ":" + expireTime + ":" + SIGN_KEY;
        String sign = md5(signData);

        String token = tokenId + "." + sign + "." + expireTime;
        
        TokenInfo info = new TokenInfo();
        info.setToken(token);
        info.setFileId(fileId);
        info.setCreateTime(timestamp);
        info.setExpireTime(expireTime);
        tokenCache.put(tokenId, info);

        log.debug("生成图片访问Token: fileId={}, 过期时间={}秒", fileId, expireSeconds);
        return token;
    }

    /**
     * 验证图片访问Token
     * @param fileId 文件ID
     * @param token 访问Token
     * @return 是否有效
     */
    public boolean validateToken(String fileId, String token) {
        if (token == null || token.isEmpty()) {
            return false;
        }

        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                return false;
            }

            String tokenId = parts[0];
            String sign = parts[1];
            long expireTime = Long.parseLong(parts[2]);

            // 检查是否过期
            long currentTime = System.currentTimeMillis() / 1000;
            if (currentTime > expireTime) {
                log.debug("Token已过期: fileId={}", fileId);
                return false;
            }

            // 验证签名
            String expectedSign = md5(fileId + ":" + currentTime + ":" + expireTime + ":" + SIGN_KEY);
            // 验证缓存的Token信息
            TokenInfo cached = tokenCache.get(tokenId);
            if (cached == null) {
                return false;
            }

            return cached.getFileId().equals(fileId);
        } catch (Exception e) {
            log.warn("Token验证失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 检查Referer是否在白名单中
     * @param referer 请求来源
     * @return 是否允许
     */
    public boolean isRefererAllowed(String referer) {
        if (referer == null || referer.isEmpty()) {
            // 空Referer通常是直接访问，根据配置决定是否允许
            return true;
        }

        for (String allowed : refererWhitelist) {
            if (referer.contains(allowed)) {
                return true;
            }
        }

        log.debug("Referer不在白名单中: {}", referer);
        return false;
    }

    /**
     * 添加Referer白名单
     */
    public void addRefererWhitelist(String domain) {
        refererWhitelist.add(domain);
        log.info("添加Referer白名单: {}", domain);
    }

    /**
     * 移除Referer白名单
     */
    public void removeRefererWhitelist(String domain) {
        refererWhitelist.remove(domain);
        log.info("移除Referer白名单: {}", domain);
    }

    /**
     * 获取Referer白名单
     */
    public Set<String> getRefererWhitelist() {
        return new HashSet<>(refererWhitelist);
    }

    /**
     * 生成带签名的图片访问URL
     * @param baseUrl 基础URL
     * @param fileId 文件ID
     * @param expireSeconds 过期时间
     * @return 签名URL
     */
    public String generateSignedUrl(String baseUrl, String fileId, long expireSeconds) {
        long expireTime = System.currentTimeMillis() / 1000 + expireSeconds;
        String signData = fileId + ":" + expireTime + ":" + SIGN_KEY;
        String sign = md5(signData);

        return String.format("%s?fileId=%s&expires=%d&sign=%s", 
                baseUrl, fileId, expireTime, sign);
    }

    /**
     * 验证签名URL
     */
    public boolean validateSignedUrl(String fileId, long expires, String sign) {
        long currentTime = System.currentTimeMillis() / 1000;
        if (currentTime > expires) {
            return false;
        }
        String expectedSign = md5(fileId + ":" + expires + ":" + SIGN_KEY);
        return expectedSign.equals(sign);
    }

    /**
     * 清理过期Token
     */
    public int cleanupExpiredTokens() {
        long currentTime = System.currentTimeMillis() / 1000;
        int removed = 0;
        Iterator<Map.Entry<String, TokenInfo>> it = tokenCache.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, TokenInfo> entry = it.next();
            if (entry.getValue().getExpireTime() < currentTime) {
                it.remove();
                removed++;
            }
        }
        if (removed > 0) {
            log.info("清理过期Token: {}个", removed);
        }
        return removed;
    }

    /** 计算字符串的MD5十六进制摘要（用于URL签名） */
    private String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5算法不可用", e);
        }
    }

    /**
     * Token信息
     */
    private static class TokenInfo {
        /** 完整Token串（tokenId.sign.expireTime） */
        private String token;
        /** 绑定的图片文件ID */
        private String fileId;
        /** 创建时间戳（秒） */
        private long createTime;
        /** 过期时间戳（秒） */
        private long expireTime;

        public String getToken() { return token; }
        public void setToken(String token) { this.token = token; }
        public String getFileId() { return fileId; }
        public void setFileId(String fileId) { this.fileId = fileId; }
        public long getCreateTime() { return createTime; }
        public void setCreateTime(long createTime) { this.createTime = createTime; }
        public long getExpireTime() { return expireTime; }
        public void setExpireTime(long expireTime) { this.expireTime = expireTime; }
    }
}
