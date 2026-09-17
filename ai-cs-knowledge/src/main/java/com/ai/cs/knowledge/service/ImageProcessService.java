package com.ai.cs.knowledge.service;

import com.ai.cs.common.util.FileMagicValidator;
import com.ai.cs.knowledge.config.ImageProperties;
import com.ai.cs.knowledge.entity.ImageMetadata;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.geometry.Positions;
import org.apache.commons.imaging.ImagingException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.UUID;

/**
 * 图片处理服务（占位）
 *
 * <p>TODO 后续实现：按阈值压缩（Thumbnailator）、缩略图生成、格式转换、尺寸调整、
 * 裁剪、水印叠加、EXIF 抽取（commons-imaging），以及上传后按配置自动向量化入库。</p>
 *
 * <p>当前保留：格式与魔数校验、文件大小校验、原始文件落盘、本地 ImageIO 解码取宽高、
 * 按扩展名映射格式/MIME、文件删除与存储目录初始化（本地 IO 与安全校验）。</p>
 *
 * <p>当前占位：{@link #extractMetadata} 保留本地解码，EXIF 抽取记日志跳过；
 * {@link #compressImage}、{@link #generateThumbnail}、{@link #convertFormat}、
 * {@link #resizeImage}、{@link #cropImage}、{@link #addWatermark} 一律抛 {@code IOException}
 * （与服务不可用语义一致）。{@link #uploadAndProcess} 仍会真正落盘，但压缩、缩略图与
 * 自动向量化均不再执行。</p>
 */
@Slf4j
@Service
public class ImageProcessService {

    private final ImageProperties imageProperties;
    private ImageVectorService imageVectorService;

    public ImageProcessService(ImageProperties imageProperties,
                                org.springframework.beans.factory.ObjectProvider<ImageVectorService> imageVectorServiceProvider) {
        this.imageProperties = imageProperties;
        this.imageVectorService = imageVectorServiceProvider.getIfAvailable();
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
     * 上传图片并保存（占位：仅校验、落盘与本地解码，不做压缩/缩略图/向量化）
     *
     * @param file 上传的图片文件
     * @return 图片元数据（含文件级信息与本地解码宽高）
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
        
        // 5. 提取元数据（本地解码，EXIF 未接入）
        ImageMetadata metadata = extractMetadata(storagePath.toFile());
        metadata.setFileId(fileId);
        metadata.setOriginalFilename(originalFilename);
        metadata.setStoragePath(storagePath.toString());
        metadata.setFileSize(file.getSize());
        metadata.setUploadTime(LocalDateTime.now());

        // 6. 压缩、缩略图与自动向量化均未接入（占位）
        log.info("[占位] 图片压缩/缩略图/自动向量化未实现 fileId={}", fileId);

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
        FileMagicValidator.assertAllowed(file, imageProperties.getAllowedFormats());
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
     * 提取图片元数据（保留本地 ImageIO 解码；EXIF 抽取为占位）
     *
     * @param imageFile 图片文件
     * @return 图片元数据（宽高/格式/MIME）
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

        // EXIF 抽取（占位：未接入 commons-imaging）
        log.info("[占位] 图片 EXIF 提取未实现 file={}", imageFile.getName());

        return metadata;
    }

    /**
     * 压缩图片（占位：不压缩，抛 IOException）
     *
     * @param sourceFile 源文件
     * @param metadata   图片元数据
     * @throws IOException 占位实现
     */
    public void compressImage(File sourceFile, ImageMetadata metadata) throws IOException {
        log.warn("[占位] 图片压缩未实现 file={}", sourceFile == null ? null : sourceFile.getName());
        throw new IOException("图片压缩为占位实现，后端未接入图像压缩");
    }

    /**
     * 生成缩略图（占位：不生成，抛 IOException）
     *
     * @param sourceFile 源文件
     * @param fileId     文件ID
     * @param extension  扩展名
     * @return 不返回（抛异常）
     * @throws IOException 占位实现
     */
    public String generateThumbnail(File sourceFile, String fileId, String extension) throws IOException {
        log.warn("[占位] 缩略图生成未实现 fileId={}", fileId);
        throw new IOException("缩略图生成为占位实现，后端未接入图像缩放");
    }

    /**
     * 图片格式转换（占位：不转换，抛 IOException）
     *
     * @param sourceFile   源文件
     * @param targetFormat 目标格式（jpg, png, gif等）
     * @return 不返回（抛异常）
     * @throws IOException 占位实现
     */
    public String convertFormat(File sourceFile, String targetFormat) throws IOException {
        log.warn("[占位] 图片格式转换未实现 targetFormat={}", targetFormat);
        throw new IOException("图片格式转换为占位实现，后端未接入图像转码");
    }

    /**
     * 调整图片尺寸（占位：不调整，抛 IOException）
     *
     * @param sourceFile      源文件
     * @param width           目标宽度
     * @param height          目标高度
     * @param keepAspectRatio 是否保持宽高比
     * @return 不返回（抛异常）
     * @throws IOException 占位实现
     */
    public String resizeImage(File sourceFile, int width, int height, boolean keepAspectRatio) 
            throws IOException {
        log.warn("[占位] 图片尺寸调整未实现 width={} height={}", width, height);
        throw new IOException("图片尺寸调整为占位实现，后端未接入图像缩放");
    }

    /**
     * 裁剪图片（占位：不裁剪，抛 IOException）
     *
     * @param sourceFile 源文件
     * @param x          起始X坐标
     * @param y          起始Y坐标
     * @param width      裁剪宽度
     * @param height     裁剪高度
     * @return 不返回（抛异常）
     * @throws IOException 占位实现
     */
    public String cropImage(File sourceFile, int x, int y, int width, int height) 
            throws IOException {
        log.warn("[占位] 图片裁剪未实现 ({},{}) {}x{}", x, y, width, height);
        throw new IOException("图片裁剪为占位实现，后端未接入图像裁剪");
    }

    /**
     * 添加水印（占位：不叠加，抛 IOException）
     *
     * @param sourceFile    源文件
     * @param watermarkFile 水印图片文件
     * @param position      水印位置
     * @param opacity       透明度（0.0-1.0）
     * @return 不返回（抛异常）
     * @throws IOException 占位实现
     */
    public String addWatermark(File sourceFile, File watermarkFile, Positions position, float opacity) 
            throws IOException {
        log.warn("[占位] 图片水印叠加未实现 opacity={}", opacity);
        throw new IOException("图片水印叠加为占位实现，后端未接入图像合成");
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
