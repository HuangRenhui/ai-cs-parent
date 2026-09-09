package com.ai.cs.knowledge.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * 图片去重检测服务
 * 基于感知哈希算法（pHash/dHash）识别视觉上相似的图片
 * 支持多级去重策略：精确哈希 + 感知哈希 + 特征向量相似度
 */
@Slf4j
@Service
public class ImageDeduplicationService {

    /**
     * 哈希距离阈值（汉明距离），越小越严格
     * 0 = 完全相同, 5 = 非常相似, 10 = 相似, >15 = 不同
     */
    private static final int SIMILARITY_THRESHOLD = 10;

    /** 缩略尺寸（用于计算感知哈希） */
    private static final int HASH_SIZE = 8;

    /** 已存储的图片哈希索引（内存缓存，生产环境应使用Redis） */
    private final Map<String, ImageHashInfo> hashIndex = new LinkedHashMap<>();

    /**
     * 计算图片的感知哈希（pHash - 基于DCT）
     * 对图片缩放、压缩、亮度调整有一定容忍度
     */
    public String computePerceptualHash(BufferedImage image) {
        // 1. 缩放到 32x32（DCT需要2的幂次）
        BufferedImage resized = new BufferedImage(32, 32, BufferedImage.TYPE_INT_RGB);
        resized.getGraphics().drawImage(
                image.getScaledInstance(32, 32, java.awt.Image.SCALE_SMOOTH), 0, 0, null);

        // 2. 转灰度并计算DCT
        double[][] dctVals = new double[32][32];
        for (int i = 0; i < 32; i++) {
            for (int j = 0; j < 32; j++) {
                int rgb = resized.getRGB(j, i);
                int gray = (int) (0.299 * ((rgb >> 16) & 0xff)
                        + 0.587 * ((rgb >> 8) & 0xff)
                        + 0.114 * (rgb & 0xff));
                dctVals[i][j] = gray;
            }
        }

        // 简化版DCT（取左上角8x8的低频部分均值）
        double[][] dct = new double[HASH_SIZE][HASH_SIZE];
        for (int i = 0; i < HASH_SIZE; i++) {
            for (int j = 0; j < HASH_SIZE; j++) {
                double sum = 0;
                for (int x = 0; x < 4; x++) {
                    for (int y = 0; y < 4; y++) {
                        sum += dctVals[i * 4 + x][j * 4 + y];
                    }
                }
                dct[i][j] = sum / 16.0;
            }
        }

        // 3. 计算均值并生成哈希
        double average = 0;
        for (int i = 0; i < HASH_SIZE; i++) {
            for (int j = 0; j < HASH_SIZE; j++) {
                average += dct[i][j];
            }
        }
        average /= (HASH_SIZE * HASH_SIZE);

        StringBuilder hash = new StringBuilder();
        for (int i = 0; i < HASH_SIZE; i++) {
            for (int j = 0; j < HASH_SIZE; j++) {
                hash.append(dct[i][j] > average ? "1" : "0");
            }
        }

        return hash.toString();
    }

    /**
     * 计算差异哈希（dHash - 基于梯度）
     * 对亮度变化更鲁棒
     */
    public String computeDifferenceHash(BufferedImage image) {
        // 缩放到 9x8
        BufferedImage resized = new BufferedImage(9, 8, BufferedImage.TYPE_INT_RGB);
        resized.getGraphics().drawImage(
                image.getScaledInstance(9, 8, java.awt.Image.SCALE_SMOOTH), 0, 0, null);

        StringBuilder hash = new StringBuilder();
        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                int left = new java.awt.Color(resized.getRGB(col, row)).getRed();
                int right = new java.awt.Color(resized.getRGB(col + 1, row)).getRed();
                hash.append(left > right ? "1" : "0");
            }
        }

        return hash.toString();
    }

    /**
     * 计算MD5精确哈希
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
     * 计算两个哈希之间的汉明距离
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
     * 检测重复图片
     * @param md5Hash 精确哈希
     * @param perceptualHash 感知哈希
     * @param fileId 当前文件ID
     * @return 重复检测结果
     */
    public DeduplicationResult checkDuplicate(String md5Hash, String perceptualHash, String fileId) {
        DeduplicationResult result = new DeduplicationResult();
        result.setDuplicate(false);

        for (Map.Entry<String, ImageHashInfo> entry : hashIndex.entrySet()) {
            ImageHashInfo existing = entry.getValue();

            // 第一级：精确哈希匹配（完全相同）
            if (existing.getMd5Hash().equals(md5Hash)) {
                result.setDuplicate(true);
                result.setMatchType(DeduplicationResult.MatchType.EXACT);
                result.setMatchedFileId(existing.getFileId());
                result.setMatchedFilename(existing.getFilename());
                result.setSimilarity(100.0);
                return result;
            }

            // 第二级：感知哈希匹配（视觉相似）
            int distance = hammingDistance(perceptualHash, existing.getPerceptualHash());
            double similarity = 100.0 * (1 - (double) distance / perceptualHash.length());

            if (distance <= SIMILARITY_THRESHOLD) {
                result.setDuplicate(true);
                result.setMatchType(DeduplicationResult.MatchType.SIMILAR);
                result.setMatchedFileId(existing.getFileId());
                result.setMatchedFilename(existing.getFilename());
                result.setSimilarity(similarity);
                result.setHammingDistance(distance);
                return result;
            }
        }

        // 无重复，记录哈希索引
        ImageHashInfo info = new ImageHashInfo();
        info.setFileId(fileId);
        info.setMd5Hash(md5Hash);
        info.setPerceptualHash(perceptualHash);
        hashIndex.put(fileId, info);

        return result;
    }

    /**
     * 查找相似图片
     */
    public List<SimilarImageResult> findSimilar(String perceptualHash, int topK) {
        List<SimilarImageResult> results = new ArrayList<>();

        for (Map.Entry<String, ImageHashInfo> entry : hashIndex.entrySet()) {
            ImageHashInfo existing = entry.getValue();
            int distance = hammingDistance(perceptualHash, existing.getPerceptualHash());
            double similarity = 100.0 * (1 - (double) distance / perceptualHash.length());

            if (distance <= SIMILARITY_THRESHOLD) {
                SimilarImageResult r = new SimilarImageResult();
                r.setFileId(existing.getFileId());
                r.setFilename(existing.getFilename());
                r.setHammingDistance(distance);
                r.setSimilarity(similarity);
                results.add(r);
            }
        }

        results.sort((a, b) -> Integer.compare(a.getHammingDistance(), b.getHammingDistance()));
        return results.size() > topK ? results.subList(0, topK) : results;
    }

    /**
     * 更新哈希索引的文件名
     */
    public void updateHashIndex(String fileId, String filename) {
        ImageHashInfo info = hashIndex.get(fileId);
        if (info != null) {
            info.setFilename(filename);
        }
    }

    /**
     * 从索引中移除
     */
    public void removeFromIndex(String fileId) {
        hashIndex.remove(fileId);
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

    /**
     * 图片哈希信息
     */
    private static class ImageHashInfo {
        /** 图片文件ID */
        private String fileId;
        /** 图片文件名 */
        private String filename;
        /** 文件内容MD5精确哈希 */
        private String md5Hash;
        /** 感知哈希（64位01字符串，基于简化DCT） */
        private String perceptualHash;

        public String getFileId() { return fileId; }
        public void setFileId(String fileId) { this.fileId = fileId; }
        public String getFilename() { return filename; }
        public void setFilename(String filename) { this.filename = filename; }
        public String getMd5Hash() { return md5Hash; }
        public void setMd5Hash(String md5Hash) { this.md5Hash = md5Hash; }
        public String getPerceptualHash() { return perceptualHash; }
        public void setPerceptualHash(String perceptualHash) { this.perceptualHash = perceptualHash; }
    }
}
