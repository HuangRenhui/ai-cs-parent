package com.ai.cs.knowledge.service;

import com.ai.cs.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

/**
 * 图片去重检测服务（占位）
 *
 * <p>TODO 后续实现：感知哈希（pHash，基于 DCT）与差异哈希（dHash，基于梯度）计算、
 * 哈希索引存储（生产环境应用 Redis）、MD5 精确匹配 + 汉明距离近似匹配的多级去重、
 * 相似图片 TopK 检索。</p>
 *
 * <p>当前保留：{@link #computeMd5Hash}（纯 JDK 工具）与 {@link #hammingDistance}
 * （长度校验 + 逐位比较，纯本地），以及两个公开嵌套结果类型。</p>
 *
 * <p>当前占位：{@link #computePerceptualHash}／{@link #computeDifferenceHash} 返回空串（中性，
 * 不伪造哈希）；{@link #checkDuplicate} 抛 {@code BusinessException(503)}（不做「恒判定不重复」的伪结果）；
 * {@link #findSimilar} 返回空列表；{@link #updateHashIndex}／{@link #removeFromIndex} 仅记日志
 * （不再维护内存哈希索引）。</p>
 */
@Slf4j
@Service
public class ImageDeduplicationService {

    /**
     * 计算图片的感知哈希（占位：返回空串）
     *
     * <p>TODO 后续实现：缩放到 32x32 → 转灰度 → DCT → 取左上 8x8 低频均值生成 64 位 01 串。</p>
     *
     * @param image 图片
     * @return 空串
     */
    public String computePerceptualHash(BufferedImage image) {
        log.info("[占位] 图片感知哈希(pHash)计算未实现，返回空串");
        return "";
    }

    /**
     * 计算差异哈希（占位：返回空串）
     *
     * <p>TODO 后续实现：缩放到 9x8 → 逐像素比较左右亮度生成 64 位 01 串。</p>
     *
     * @param image 图片
     * @return 空串
     */
    public String computeDifferenceHash(BufferedImage image) {
        log.info("[占位] 图片差异哈希(dHash)计算未实现，返回空串");
        return "";
    }

    /**
     * 计算MD5精确哈希（保留：纯 JDK 工具，无外部依赖）
     */
    public String computeMd5Hash(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(data);
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
     * 计算两个哈希之间的汉明距离（保留：长度校验 + 逐位比较）
     */
    public int hammingDistance(String hash1, String hash2) {
        if (hash1.length() != hash2.length()) {
            throw new IllegalArgumentException("哈希长度不一致");
        }
        int distance = 0;
        for (int i = 0; i < hash1.length(); i++) {
            if (hash1.charAt(i) != hash2.charAt(i)) {
                distance++;
            }
        }
        return distance;
    }

    /**
     * 检测重复图片（占位：抛 503，不返回伪判定）
     *
     * @param md5Hash        精确哈希
     * @param perceptualHash 感知哈希
     * @param fileId         当前文件ID
     * @return 不返回（抛异常）
     */
    public DeduplicationResult checkDuplicate(String md5Hash, String perceptualHash, String fileId) {
        log.warn("[占位] 图片去重检测未实现 fileId={}", fileId);
        throw new BusinessException(503, "图片去重检测为占位实现，后端未接入感知哈希索引");
    }

    /**
     * 查找相似图片（占位：返回空列表）
     *
     * @param perceptualHash 感知哈希
     * @param topK           返回数量上限
     * @return 空列表
     */
    public List<SimilarImageResult> findSimilar(String perceptualHash, int topK) {
        log.info("[占位] 相似图片检索未实现 topK={}", topK);
        return List.of();
    }

    /**
     * 更新哈希索引的文件名（占位：不再维护内存索引，仅记日志）
     */
    public void updateHashIndex(String fileId, String filename) {
        log.info("[占位] 哈希索引更新未实现 fileId={}", fileId);
    }

    /**
     * 从索引中移除（占位：不再维护内存索引，仅记日志）
     */
    public void removeFromIndex(String fileId) {
        log.info("[占位] 哈希索引移除未实现 fileId={}", fileId);
    }

    /**
     * 去重检测结果
     */
    public static class DeduplicationResult {
        /** 是否判定为重复 */
        private boolean duplicate;
        /** 匹配类型 */
        private MatchType matchType;
        /** 命中的已存在文件ID */
        private String matchedFileId;
        /** 命中的已存在文件名 */
        private String matchedFilename;
        /** 相似度（百分比，0~100） */
        private double similarity;
        /** 感知哈希的汉明距离（越小越相似） */
        private int hammingDistance;

        public enum MatchType {
            EXACT,    // 精确匹配（MD5相同）
            SIMILAR,  // 感知相似（pHash接近）
            NONE      // 不重复
        }

        // Getters and Setters
        public boolean isDuplicate() { return duplicate; }
        public void setDuplicate(boolean duplicate) { this.duplicate = duplicate; }
        public MatchType getMatchType() { return matchType; }
        public void setMatchType(MatchType matchType) { this.matchType = matchType; }
        public String getMatchedFileId() { return matchedFileId; }
        public void setMatchedFileId(String matchedFileId) { this.matchedFileId = matchedFileId; }
        public String getMatchedFilename() { return matchedFilename; }
        public void setMatchedFilename(String matchedFilename) { this.matchedFilename = matchedFilename; }
        public double getSimilarity() { return similarity; }
        public void setSimilarity(double similarity) { this.similarity = similarity; }
        public int getHammingDistance() { return hammingDistance; }
        public void setHammingDistance(int hammingDistance) { this.hammingDistance = hammingDistance; }
    }

    /**
     * 相似图片结果
     */
    public static class SimilarImageResult {
        /** 相似图片文件ID */
        private String fileId;
        /** 相似图片文件名 */
        private String filename;
        /** 感知哈希的汉明距离（越小越相似） */
        private int hammingDistance;
        /** 相似度（百分比，0~100） */
        private double similarity;

        // Getters and Setters
        public String getFileId() { return fileId; }
        public void setFileId(String fileId) { this.fileId = fileId; }
        public String getFilename() { return filename; }
        public void setFilename(String filename) { this.filename = filename; }
        public int getHammingDistance() { return hammingDistance; }
        public void setHammingDistance(int hammingDistance) { this.hammingDistance = hammingDistance; }
        public double getSimilarity() { return similarity; }
        public void setSimilarity(double similarity) { this.similarity = similarity; }
    }
}
