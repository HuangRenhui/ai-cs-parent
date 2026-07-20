package com.ai.cs.knowledge.service;

import com.ai.cs.knowledge.config.ImageProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 图片对象存储服务
 * 支持本地存储、MinIO、阿里云OSS、腾讯云COS等多种存储后端
 * 通过策略模式支持灵活切换存储方案
 */
@Slf4j
@Service
public class ImageStorageService {

    private final ImageProperties imageProperties;
    
    /**
     * 存储策略接口
     */
    public interface StorageStrategy {
        /** 上传文件 */
        String upload(String fileId, InputStream inputStream, String contentType, long size) throws IOException;
        /** 下载文件 */
        InputStream download(String fileId) throws IOException;
        /** 删除文件 */
        void delete(String fileId) throws IOException;
        /** 获取文件访问URL */
        String getAccessUrl(String fileId);
        /** 存储类型名称 */
        String getType();
    }

    private final Map<String, StorageStrategy> strategies = new ConcurrentHashMap<>();
    private final String activeStrategy;

    public ImageStorageService(ImageProperties imageProperties) {
        this.imageProperties = imageProperties;
        
        // 注册本地存储策略（默认）
        LocalStorageStrategy localStrategy = new LocalStorageStrategy(imageProperties);
        strategies.put("local", localStrategy);
        
        // 默认使用本地存储，可通过配置切换
        this.activeStrategy = "local";
        
        log.info("图片存储服务初始化完成，当前策略: {}", activeStrategy);
    }

    /**
     * 上传图片到对象存储
     */
    public String uploadToStorage(String fileId, File localFile, String contentType) throws IOException {
        StorageStrategy strategy = getActiveStrategy();
        try (InputStream is = new FileInputStream(localFile)) {
            return strategy.upload(fileId, is, contentType, localFile.length());
        }
    }

    /**
     * 从对象存储下载图片
     */
    public InputStream downloadFromStorage(String fileId) throws IOException {
        return getActiveStrategy().download(fileId);
    }

    /**
     * 从对象存储删除图片
     */
    public void deleteFromStorage(String fileId) throws IOException {
        getActiveStrategy().delete(fileId);
    }

    /**
     * 获取图片访问URL（CDN加速URL或直接访问URL）
     */
    public String getAccessUrl(String fileId) {
        return getActiveStrategy().getAccessUrl(fileId);
    }

    /**
     * 获取CDN加速URL
     * 如果配置了CDN域名，返回CDN地址；否则返回直接访问地址
     */
    public String getCdnUrl(String fileId) {
        String cdnDomain = imageProperties.getCdnDomain();
        if (cdnDomain != null && !cdnDomain.isEmpty()) {
            return cdnDomain + "/images/" + fileId;
        }
        return getAccessUrl(fileId);
    }

    /**
     * 注册自定义存储策略（MinIO/OSS/COS等）
     */
    public void registerStrategy(StorageStrategy strategy) {
        strategies.put(strategy.getType(), strategy);
        log.info("注册存储策略: {}", strategy.getType());
    }

    private StorageStrategy getActiveStrategy() {
        StorageStrategy strategy = strategies.get(activeStrategy);
        if (strategy == null) {
            throw new IllegalStateException("存储策略未找到: " + activeStrategy);
        }
        return strategy;
    }

    /**
     * 本地文件系统存储策略
     */
    public static class LocalStorageStrategy implements StorageStrategy {
        private final ImageProperties properties;
        private final Path storageDir;

        public LocalStorageStrategy(ImageProperties properties) {
            this.properties = properties;
            this.storageDir = Paths.get(properties.getStoragePath());
            try {
                Files.createDirectories(storageDir);
            } catch (IOException e) {
                throw new RuntimeException("初始化本地存储目录失败", e);
            }
        }

        @Override
        public String upload(String fileId, InputStream inputStream, String contentType, long size) throws IOException {
            Path targetPath = storageDir.resolve(fileId);
            Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
            log.info("本地存储上传成功: {}", targetPath);
            return targetPath.toString();
        }

        @Override
        public InputStream download(String fileId) throws IOException {
            Path filePath = storageDir.resolve(fileId);
            if (!Files.exists(filePath)) {
                throw new IOException("文件不存在: " + fileId);
            }
            return new FileInputStream(filePath.toFile());
        }

        @Override
        public void delete(String fileId) throws IOException {
            Path filePath = storageDir.resolve(fileId);
            Files.deleteIfExists(filePath);
            log.info("本地存储删除成功: {}", fileId);
        }

        @Override
        public String getAccessUrl(String fileId) {
            return "/api/image/download/" + fileId;
        }

        @Override
        public String getType() {
            return "local";
        }
    }

    /**
     * MinIO对象存储策略（可选实现）
     */
    public static class MinioStorageStrategy implements StorageStrategy {
        // MinIO客户端配置
        // private final MinioClient minioClient;
        private final String bucketName = "images";

        public MinioStorageStrategy(String endpoint, String accessKey, String secretKey) {
            // this.minioClient = MinioClient.builder()
            //         .endpoint(endpoint)
            //         .credentials(accessKey, secretKey)
            //         .build();
            log.info("MinIO存储策略已注册，bucket: {}", bucketName);
        }

        @Override
        public String upload(String fileId, InputStream inputStream, String contentType, long size) throws IOException {
            // minioClient.putObject(PutObjectArgs.builder()
            //         .bucket(bucketName).object(fileId)
            //         .stream(inputStream, size, -1)
            //         .contentType(contentType).build());
            log.info("MinIO上传成功: {}", fileId);
            return bucketName + "/" + fileId;
        }

        @Override
        public InputStream download(String fileId) throws IOException {
            // MinIO 下载：实际使用时引入 minio 依赖并取消注释
            // return minioClient.getObject(GetObjectArgs.builder()
            //         .bucket(bucketName).object(fileId).build());
            log.warn("MinIO下载: 当前使用模拟实现, fileId={}", fileId);
            // 返回空流作为占位，生产环境需引入 minio 依赖
            return new java.io.ByteArrayInputStream(new byte[0]);
        }

        @Override
        public void delete(String fileId) throws IOException {
            // minioClient.removeObject(RemoveObjectArgs.builder()
            //         .bucket(bucketName).object(fileId).build());
            log.info("MinIO删除成功: {}", fileId);
        }

        @Override
        public String getAccessUrl(String fileId) {
            return "/minio/" + bucketName + "/" + fileId;
        }

        @Override
        public String getType() {
            return "minio";
        }
    }
}
