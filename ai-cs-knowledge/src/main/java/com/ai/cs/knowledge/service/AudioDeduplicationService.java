package com.ai.cs.knowledge.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.sound.sampled.*;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 音频指纹和去重检测服务
 * 基于音频指纹（Audio Fingerprinting）算法识别重复/相似音频
 * 采用简化的频谱峰值检测方案
 */
@Slf4j
@Service
public class AudioDeduplicationService {

    /** 相似度阈值 */
    private static final double SIMILARITY_THRESHOLD = 0.85;

    /** 指纹存储（内存缓存，生产环境应使用数据库） */
    private final Map<String, AudioFingerprint> fingerprintStore = new ConcurrentHashMap<>();

    /**
     * 计算音频的MD5精确哈希
     */
    public String computeMd5Hash(byte[] audioData) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(audioData);
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
     * 生成音频指纹（简化版频谱峰值方案）
     * 提取音频的特征值序列作为指纹
     * 
     * @param audioFile 音频文件
     * @param sampleCount 采样点数量
     * @return 指纹特征向量
     */
    public double[] generateFingerprint(File audioFile, int sampleCount) throws IOException {
        try (AudioInputStream original = AudioSystem.getAudioInputStream(audioFile)) {
            AudioFormat format = original.getFormat();
            AudioFormat pcmFormat = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    format.getSampleRate(),
                    16,
                    format.getChannels(),
                    format.getChannels() * 2,
                    format.getSampleRate(),
                    false
            );
            byte[] audioBytes;
            if (!format.matches(pcmFormat)) {
                try (AudioInputStream converted = AudioSystem.getAudioInputStream(pcmFormat, original)) {
                    audioBytes = readAllBytes(converted);
                }
            } else {
                audioBytes = readAllBytes(original);
            }
            short[] samples = new short[audioBytes.length / 2];
            ByteBuffer.wrap(audioBytes).order(ByteOrder.LITTLE_ENDIAN)
                    .asShortBuffer().get(samples);
            return computeSpectralFeatures(samples, sampleCount);
        } catch (UnsupportedAudioFileException e) {
            throw new IOException("不支持的音频格式: " + e.getMessage(), e);
        }
    }

    /**
     * 计算频谱特征向量
     */
    private double[] computeSpectralFeatures(short[] samples, int sampleCount) {
        double[] features = new double[sampleCount];

        // 将音频均分为sampleCount个窗口
        int windowSize = samples.length / sampleCount;
        if (windowSize < 2) {
            windowSize = 2;
        }

        for (int i = 0; i < sampleCount && i * windowSize < samples.length; i++) {
            int start = i * windowSize;
            int end = Math.min(start + windowSize, samples.length);

            // 计算窗口内的能量特征
            double energy = 0;
            double zeroCrossingRate = 0;
            for (int j = start; j < end; j++) {
                energy += Math.abs(samples[j]) / 32768.0;
                if (j > start) {
                    if ((samples[j] >= 0 && samples[j - 1] < 0) ||
                            (samples[j] < 0 && samples[j - 1] >= 0)) {
                        zeroCrossingRate++;
                    }
                }
            }
            energy /= (end - start);
            zeroCrossingRate /= (end - start);

            // 组合特征
            features[i] = energy * 0.6 + zeroCrossingRate * 0.4;
        }

        // 归一化
        double max = Arrays.stream(features).max().orElse(1.0);
        double min = Arrays.stream(features).min().orElse(0.0);
        if (max > min) {
            for (int i = 0; i < features.length; i++) {
                features[i] = (features[i] - min) / (max - min);
            }
        }

        return features;
    }

    /**
     * 计算两个指纹之间的余弦相似度
     */
    public double cosineSimilarity(double[] fp1, double[] fp2) {
        if (fp1.length != fp2.length) {
            throw new IllegalArgumentException("指纹维度不一致");
        }

        double dotProduct = 0;
        double norm1 = 0;
        double norm2 = 0;

        for (int i = 0; i < fp1.length; i++) {
            dotProduct += fp1[i] * fp2[i];
            norm1 += fp1[i] * fp1[i];
            norm2 += fp2[i] * fp2[i];
        }

        if (norm1 == 0 || norm2 == 0) {
            return 0;
        }

        return dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }

    /**
     * 检测重复音频
     */
    public DeduplicationResult checkDuplicate(String md5Hash, double[] fingerprint, String fileId, String filename) {
        DeduplicationResult result = new DeduplicationResult();
        result.setDuplicate(false);

        for (Map.Entry<String, AudioFingerprint> entry : fingerprintStore.entrySet()) {
            AudioFingerprint existing = entry.getValue();

            // 精确匹配
            if (existing.getMd5Hash().equals(md5Hash)) {
                result.setDuplicate(true);
                result.setMatchType(DeduplicationResult.MatchType.EXACT);
                result.setMatchedFileId(existing.getFileId());
                result.setMatchedFilename(existing.getFilename());
                result.setSimilarity(100.0);
                return result;
            }

            // 指纹相似度匹配
            double similarity = cosineSimilarity(fingerprint, existing.getFingerprint());
            if (similarity >= SIMILARITY_THRESHOLD) {
                result.setDuplicate(true);
                result.setMatchType(DeduplicationResult.MatchType.SIMILAR);
                result.setMatchedFileId(existing.getFileId());
                result.setMatchedFilename(existing.getFilename());
                result.setSimilarity(similarity * 100);
                return result;
            }
        }

        // 不重复，存入指纹库
        AudioFingerprint af = new AudioFingerprint();
        af.setFileId(fileId);
        af.setFilename(filename);
        af.setMd5Hash(md5Hash);
        af.setFingerprint(fingerprint);
        fingerprintStore.put(fileId, af);

        return result;
    }

    /**
     * 查找相似音频
     */
    public List<SimilarResult> findSimilar(double[] fingerprint, int topK) {
        List<SimilarResult> results = new ArrayList<>();

        for (Map.Entry<String, AudioFingerprint> entry : fingerprintStore.entrySet()) {
            AudioFingerprint existing = entry.getValue();
            double similarity = cosineSimilarity(fingerprint, existing.getFingerprint());

            if (similarity >= SIMILARITY_THRESHOLD) {
                SimilarResult r = new SimilarResult();
                r.setFileId(existing.getFileId());
                r.setFilename(existing.getFilename());
                r.setSimilarity(similarity * 100);
                results.add(r);
            }
        }

        results.sort((a, b) -> Double.compare(b.getSimilarity(), a.getSimilarity()));
        return results.size() > topK ? results.subList(0, topK) : results;
    }

    /**
     * 从指纹库中移除
     */
    public void removeFingerprint(String fileId) {
        fingerprintStore.remove(fileId);
    }

    /**
     * 读取音频流全部字节（用于指纹计算的PCM数据提取）
     */
    private byte[] readAllBytes(AudioInputStream audioStream) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int bytesRead;
        while ((bytesRead = audioStream.read(buffer)) != -1) {
            baos.write(buffer, 0, bytesRead);
        }
        return baos.toByteArray();
    }

    /**
     * 去重检测结果
     */
    public static class DeduplicationResult {
        /** 是否判定为重复 */
        private boolean duplicate;
        /** 匹配类型：EXACT=MD5完全相同，SIMILAR=指纹相似，NONE=不重复 */
        private MatchType matchType;
        /** 命中的已存在文件ID */
        private String matchedFileId;
        /** 命中的已存在文件名 */
        private String matchedFilename;
        /** 相似度（百分比，0~100） */
        private double similarity;

        public enum MatchType {
            /** 精确匹配（MD5相同） */
            EXACT,
            /** 指纹相似匹配 */
            SIMILAR,
            /** 不重复 */
            NONE
        }

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
    }

    /**
     * 相似结果
     */
    public static class SimilarResult {
        /** 相似音频文件ID */
        private String fileId;
        /** 相似音频文件名 */
        private String filename;
        /** 相似度（百分比，0~100） */
        private double similarity;

        public String getFileId() { return fileId; }
        public void setFileId(String fileId) { this.fileId = fileId; }
        public String getFilename() { return filename; }
        public void setFilename(String filename) { this.filename = filename; }
        public double getSimilarity() { return similarity; }
        public void setSimilarity(double similarity) { this.similarity = similarity; }
    }

    /**
     * 音频指纹
     */
    private static class AudioFingerprint {
        /** 音频文件ID */
        private String fileId;
        /** 音频文件名 */
        private String filename;
        /** 文件内容MD5精确哈希 */
        private String md5Hash;
        /** 音频指纹特征向量（能量+过零率的组合特征） */
        private double[] fingerprint;

        public String getFileId() { return fileId; }
        public void setFileId(String fileId) { this.fileId = fileId; }
        public String getFilename() { return filename; }
        public void setFilename(String filename) { this.filename = filename; }
        public String getMd5Hash() { return md5Hash; }
        public void setMd5Hash(String md5Hash) { this.md5Hash = md5Hash; }
        public double[] getFingerprint() { return fingerprint; }
        public void setFingerprint(double[] fingerprint) { this.fingerprint = fingerprint; }
    }
}
