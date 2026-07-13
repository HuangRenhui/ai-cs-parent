package com.ai.cs.knowledge.service;

import com.ai.cs.knowledge.config.ImageProperties;
import com.ai.cs.knowledge.entity.ImageMetadata;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import net.coobird.thumbnailator.geometry.Positions;
import org.apache.commons.imaging.Imaging;
import org.apache.commons.imaging.ImagingException;
import org.apache.commons.imaging.formats.jpeg.exif.ExifRewriter;
import org.apache.commons.imaging.formats.jpeg.iptc.JpegIptcRewriter;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.UUID;

/**
 * 图片处理服务
 * 提供图片上传、压缩、元数据提取、格式转换、缩略图生成等功能
 */
@Slf4j
@Service
public class ImageProcessService {

    private final ImageProperties imageProperties;

    public ImageProcessService(ImageProperties imageProperties) {
        this.imageProperties = imageProperties;
        // 初始化存储目录
        initDirectories();
    }

    /**
     * 初始化存储目录
     */
    private void initDirectories() {
        try {
            Files.createDirectories(Paths.get(imageProperties.getStoragePath()));
            Files.createDirectories(Paths.get(imageProperties.getThumbnailPath()));
            log.info("图片存储目录初始化完成: {}", imageProperties.getStoragePath());
            log.info("缩略图存储目录初始化完成: {}", imageProperties.getThumbnailPath());
        } catch (IOException e) {
            log.error("初始化存储目录失败", e);
            throw new RuntimeException("初始化存储目录失败", e);
        }
    }

    /**
     * 上传图片并处理
     * @param file 上传的图片文件
     * @return 图片元数据
     */
    public ImageMetadata uploadAndProcess(MultipartFile file) throws IOException, ImagingException {
        // 1. 验证文件格式
        validateImageFormat(file);
        
        // 2. 验证文件大小
        validateFileSize(file);
        
        // 3. 生成唯一文件ID
        String fileId = UUID.randomUUID().toString();
        String originalFilename = file.getOriginalFilename();
        String extension = getFileExtension(originalFilename);
        
        // 4. 保存原始文件
        String storageFileName = fileId + "." + extension;
        Path storagePath = Paths.get(imageProperties.getStoragePath(), storageFileName);
        file.transferTo(storagePath.toFile());
        
        log.info("图片已保存: {}", storagePath);
        
        // 5. 提取元数据
        ImageMetadata metadata = extractMetadata(storagePath.toFile());
        metadata.setFileId(fileId);
        metadata.setOriginalFilename(originalFilename);
        metadata.setStoragePath(storagePath.toString());
        metadata.setFileSize(file.getSize());
        metadata.setUploadTime(LocalDateTime.now());
        
        // 6. 自动压缩（如果超过阈值）
        if (metadata.getFileSize() > imageProperties.getCompressThreshold() * 1024) {
            compressImage(storagePath.toFile(), metadata);
        }
        
        // 7. 生成缩略图
        String thumbnailPath = generateThumbnail(storagePath.toFile(), fileId, extension);
        metadata.setThumbnailPath(thumbnailPath);
        
        return metadata;
    }

    /**
     * 验证图片格式
     */
    private void validateImageFormat(MultipartFile file) {
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.contains(".")) {
            throw new IllegalArgumentException("无效的文件名");
        }
        
        String extension = getFileExtension(filename).toLowerCase();
        boolean allowed = Arrays.stream(imageProperties.getAllowedFormats())
                .anyMatch(format -> format.equalsIgnoreCase(extension));
        
        if (!allowed) {
            throw new IllegalArgumentException("不支持的图片格式: " + extension + 
                    ", 支持的格式: " + Arrays.toString(imageProperties.getAllowedFormats()));
        }
    }

    /**
     * 验证文件大小
     */
    private void validateFileSize(MultipartFile file) {
        long maxSizeBytes = imageProperties.getMaxFileSize() * 1024L * 1024L;
        if (file.getSize() > maxSizeBytes) {
            throw new IllegalArgumentException("文件大小超过限制: " + 
                    (file.getSize() / 1024 / 1024) + "MB, 最大允许: " + 
                    imageProperties.getMaxFileSize() + "MB");
        }
    }

    /**
     * 获取文件扩展名
     */
    private String getFileExtension(String filename) {
        int lastDotIndex = filename.lastIndexOf(".");
        return lastDotIndex > 0 ? filename.substring(lastDotIndex + 1) : "";
    }

    /**
     * 提取图片元数据（包括EXIF信息）
     */
    public ImageMetadata extractMetadata(File imageFile) throws IOException, ImagingException {
        ImageMetadata metadata = new ImageMetadata();
        
        // 读取基本信息
        BufferedImage image = ImageIO.read(imageFile);
        if (image != null) {
            metadata.setWidth(image.getWidth());
            metadata.setHeight(image.getHeight());
        }
        
        // 获取文件格式和MIME类型
        String fileName = imageFile.getName();
        String extension = getFileExtension(fileName).toLowerCase();
        metadata.setFormat(extension);
        metadata.setMimeType(getMimeType(extension));
        
        // 提取EXIF信息
        try {
            org.apache.commons.imaging.common.ImageMetadata exifMetadata = 
                    Imaging.getMetadata(imageFile);
            
            if (exifMetadata != null) {
                // 这里可以进一步解析EXIF字段
                // 由于Commons Imaging的API较复杂，这里简化处理
                log.debug("成功提取EXIF信息: {}", imageFile.getName());
            }
        } catch (Exception e) {
            log.warn("提取EXIF信息失败: {}, 错误: {}", imageFile.getName(), e.getMessage());
        }
        
        return metadata;
    }

    /**
     * 压缩图片
     */
    public void compressImage(File sourceFile, ImageMetadata metadata) throws IOException {
        long originalSize = sourceFile.length();
        
        // 使用Thumbnailator进行压缩
        Thumbnails.of(sourceFile)
                .scale(1.0)  // 保持原尺寸
                .outputQuality(imageProperties.getCompressQuality())
                .toFile(sourceFile);
        
        long compressedSize = sourceFile.length();
        double ratio = (double) compressedSize / originalSize;
        
        metadata.setCompressed(true);
        metadata.setCompressedSize(compressedSize);
        metadata.setCompressionRatio(ratio);
        
        log.info("图片压缩完成: {} -> {} bytes, 压缩比: {:.2f}", 
                originalSize, compressedSize, ratio);
    }

    /**
     * 生成缩略图
     */
    public String generateThumbnail(File sourceFile, String fileId, String extension) throws IOException {
        String thumbnailFileName = fileId + "_thumb." + extension;
        Path thumbnailPath = Paths.get(imageProperties.getThumbnailPath(), thumbnailFileName);
        
        Thumbnails.of(sourceFile)
                .size(imageProperties.getThumbnailWidth(), imageProperties.getThumbnailHeight())
                .keepAspectRatio(true)
                .toFile(thumbnailPath.toFile());
        
        log.info("缩略图已生成: {}", thumbnailPath);
        return thumbnailPath.toString();
    }

    /**
     * 图片格式转换
     * @param sourceFile 源文件
     * @param targetFormat 目标格式（jpg, png, gif等）
     * @return 转换后的文件路径
     */
    public String convertFormat(File sourceFile, String targetFormat) throws IOException {
        String fileId = UUID.randomUUID().toString();
        String targetFileName = fileId + "." + targetFormat.toLowerCase();
        Path targetPath = Paths.get(imageProperties.getStoragePath(), targetFileName);
        
        BufferedImage image = ImageIO.read(sourceFile);
        ImageIO.write(image, targetFormat, targetPath.toFile());
        
        log.info("图片格式转换完成: {} -> {}", sourceFile.getName(), targetPath);
        return targetPath.toString();
    }

    /**
     * 调整图片尺寸
     * @param sourceFile 源文件
     * @param width 目标宽度
     * @param height 目标高度
     * @param keepAspectRatio 是否保持宽高比
     * @return 调整后的文件路径
     */
    public String resizeImage(File sourceFile, int width, int height, boolean keepAspectRatio) 
            throws IOException {
        String fileId = UUID.randomUUID().toString();
        String extension = getFileExtension(sourceFile.getName());
        String resizedFileName = fileId + "_resized." + extension;
        Path resizedPath = Paths.get(imageProperties.getStoragePath(), resizedFileName);
        
        if (keepAspectRatio) {
            Thumbnails.of(sourceFile)
                    .size(width, height)
                    .keepAspectRatio(true)
                    .toFile(resizedPath.toFile());
        } else {
            Thumbnails.of(sourceFile)
                    .size(width, height)
                    .keepAspectRatio(false)
                    .toFile(resizedPath.toFile());
        }
        
        log.info("图片尺寸调整完成: {}x{} -> {}", width, height, resizedPath);
        return resizedPath.toString();
    }

    /**
     * 裁剪图片
     * @param sourceFile 源文件
     * @param x 起始X坐标
     * @param y 起始Y坐标
     * @param width 裁剪宽度
     * @param height 裁剪高度
     * @return 裁剪后的文件路径
     */
    public String cropImage(File sourceFile, int x, int y, int width, int height) 
            throws IOException {
        String fileId = UUID.randomUUID().toString();
        String extension = getFileExtension(sourceFile.getName());
        String croppedFileName = fileId + "_cropped." + extension;
        Path croppedPath = Paths.get(imageProperties.getStoragePath(), croppedFileName);
        
        Thumbnails.of(sourceFile)
                .sourceRegion(x, y, width, height)
                .toFile(croppedPath.toFile());
        
        log.info("图片裁剪完成: ({},{}) {}x{} -> {}", x, y, width, height, croppedPath);
        return croppedPath.toString();
    }

    /**
     * 添加水印
     * @param sourceFile 源文件
     * @param watermarkFile 水印图片文件
     * @param position 水印位置
     * @param opacity 透明度（0.0-1.0）
     * @return 添加水印后的文件路径
     */
    public String addWatermark(File sourceFile, File watermarkFile, Positions position, float opacity) 
            throws IOException {
        String fileId = UUID.randomUUID().toString();
        String extension = getFileExtension(sourceFile.getName());
        String watermarkedFileName = fileId + "_watermark." + extension;
        Path watermarkedPath = Paths.get(imageProperties.getStoragePath(), watermarkedFileName);
        
        Thumbnails.of(sourceFile)
                .watermark(position, ImageIO.read(watermarkFile), opacity)
                .toFile(watermarkedPath.toFile());
        
        log.info("水印添加完成: {}", watermarkedPath);
        return watermarkedPath.toString();
    }

    /**
     * 根据扩展名获取MIME类型
     */
    private String getMimeType(String extension) {
        return switch (extension.toLowerCase()) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            case "bmp" -> "image/bmp";
            default -> "application/octet-stream";
        };
    }

    /**
     * 删除图片文件
     */
    public void deleteImage(String filePath) {
        try {
            Path path = Paths.get(filePath);
            Files.deleteIfExists(path);
            log.info("图片文件已删除: {}", filePath);
        } catch (IOException e) {
            log.error("删除图片文件失败: {}", filePath, e);
        }
    }
}

