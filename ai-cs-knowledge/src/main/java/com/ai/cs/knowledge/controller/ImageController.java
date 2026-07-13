package com.ai.cs.knowledge.controller;

import com.ai.cs.common.result.Result;
import com.ai.cs.knowledge.entity.ImageMetadata;
import com.ai.cs.knowledge.service.ImageProcessService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * 图片上传与处理控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/image")
@Tag(name = "图片管理", description = "图片上传、处理、格式转换等接口")
public class ImageController {

    private final ImageProcessService imageProcessService;

    public ImageController(ImageProcessService imageProcessService) {
        this.imageProcessService = imageProcessService;
    }

    /**
     * 上传图片并自动处理
     */
    @PostMapping("/upload")
    @Operation(summary = "上传图片", description = "上传图片并自动进行压缩、生成缩略图、提取元数据等处理")
    public Result<Map<String, Object>> uploadImage(
            @Parameter(description = "图片文件") @RequestParam("file") MultipartFile file) {
        try {
            log.info("开始上传图片: {}, 大小: {} bytes", 
                    file.getOriginalFilename(), file.getSize());
            
            ImageMetadata metadata = imageProcessService.uploadAndProcess(file);
            
            Map<String, Object> result = new HashMap<>();
            result.put("fileId", metadata.getFileId());
            result.put("originalFilename", metadata.getOriginalFilename());
            result.put("storagePath", metadata.getStoragePath());
            result.put("thumbnailPath", metadata.getThumbnailPath());
            result.put("fileSize", metadata.getFileSize());
            result.put("compressedSize", metadata.getCompressedSize());
            result.put("width", metadata.getWidth());
            result.put("height", metadata.getHeight());
            result.put("format", metadata.getFormat());
            result.put("compressed", metadata.isCompressed());
            result.put("compressionRatio", metadata.getCompressionRatio());
            result.put("uploadTime", metadata.getUploadTime());
            
            log.info("图片上传处理完成: {}", metadata.getFileId());
            return Result.success(result);
            
        } catch (IllegalArgumentException e) {
            log.warn("图片上传失败 - 参数错误: {}", e.getMessage());
            return Result.fail(e.getMessage());
        } catch (Exception e) {
            log.error("图片上传处理异常", e);
            return Result.fail("图片上传失败: " + e.getMessage());
        }
    }

    /**
     * 获取图片元数据
     */
    @GetMapping("/metadata/{fileId}")
    @Operation(summary = "获取图片元数据", description = "根据文件ID获取图片的详细信息和EXIF数据")
    public Result<ImageMetadata> getImageMetadata(
            @Parameter(description = "文件ID") @PathVariable String fileId) {
        try {
            // 这里应该从数据库或缓存中获取元数据
            // 简化实现：返回基本信息
            ImageMetadata metadata = new ImageMetadata();
            metadata.setFileId(fileId);
            return Result.success(metadata);
        } catch (Exception e) {
            log.error("获取图片元数据失败", e);
            return Result.fail("获取元数据失败: " + e.getMessage());
        }
    }

    /**
     * 下载图片
     */
    @GetMapping("/download/{fileId}")
    @Operation(summary = "下载图片", description = "根据文件ID下载原始图片")
    public ResponseEntity<Resource> downloadImage(
            @Parameter(description = "文件ID") @PathVariable String fileId,
            @Parameter(description = "是否下载缩略图") @RequestParam(defaultValue = "false") boolean thumbnail) {
        try {
            // 构建文件路径（实际应从数据库查询）
            String basePath = thumbnail ? "./uploads/thumbnails" : "./uploads/images";
            Path filePath = Paths.get(basePath, fileId + ".*");
            
            // 查找匹配的文件
            File[] files = new File(basePath).listFiles((dir, name) -> name.startsWith(fileId));
            if (files == null || files.length == 0) {
                return ResponseEntity.notFound().build();
            }
            
            File targetFile = files[0];
            Resource resource = new FileSystemResource(targetFile);
            
            String contentType = Files.probeContentType(targetFile.toPath());
            if (contentType == null) {
                contentType = "application/octet-stream";
            }
            
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, 
                            "attachment; filename=\"" + targetFile.getName() + "\"")
                    .body(resource);
                    
        } catch (Exception e) {
            log.error("下载图片失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 图片格式转换
     */
    @PostMapping("/convert")
    @Operation(summary = "图片格式转换", description = "将图片转换为指定格式")
    public Result<Map<String, String>> convertFormat(
            @Parameter(description = "图片文件") @RequestParam("file") MultipartFile file,
            @Parameter(description = "目标格式(jpg/png/gif/webp)") @RequestParam String targetFormat) {
        try {
            // 先保存临时文件
            String tempPath = System.getProperty("java.io.tmpdir") + "/" + 
                    System.currentTimeMillis() + "_" + file.getOriginalFilename();
            file.transferTo(new File(tempPath));
            
            // 执行格式转换
            String convertedPath = imageProcessService.convertFormat(
                    new File(tempPath), targetFormat);
            
            // 删除临时文件
            new File(tempPath).delete();
            
            Map<String, String> result = new HashMap<>();
            result.put("convertedPath", convertedPath);
            result.put("targetFormat", targetFormat);
            
            return Result.success(result);
            
        } catch (Exception e) {
            log.error("图片格式转换失败", e);
            return Result.fail("格式转换失败: " + e.getMessage());
        }
    }

    /**
     * 调整图片尺寸
     */
    @PostMapping("/resize")
    @Operation(summary = "调整图片尺寸", description = "调整图片到指定宽高")
    public Result<Map<String, String>> resizeImage(
            @Parameter(description = "图片文件") @RequestParam("file") MultipartFile file,
            @Parameter(description = "目标宽度") @RequestParam int width,
            @Parameter(description = "目标高度") @RequestParam int height,
            @Parameter(description = "保持宽高比") @RequestParam(defaultValue = "true") boolean keepAspectRatio) {
        try {
            // 先保存临时文件
            String tempPath = System.getProperty("java.io.tmpdir") + "/" + 
                    System.currentTimeMillis() + "_" + file.getOriginalFilename();
            file.transferTo(new File(tempPath));
            
            // 执行尺寸调整
            String resizedPath = imageProcessService.resizeImage(
                    new File(tempPath), width, height, keepAspectRatio);
            
            // 删除临时文件
            new File(tempPath).delete();
            
            Map<String, String> result = new HashMap<>();
            result.put("resizedPath", resizedPath);
            result.put("width", String.valueOf(width));
            result.put("height", String.valueOf(height));
            
            return Result.success(result);
            
        } catch (Exception e) {
            log.error("图片尺寸调整失败", e);
            return Result.fail("尺寸调整失败: " + e.getMessage());
        }
    }

    /**
     * 裁剪图片
     */
    @PostMapping("/crop")
    @Operation(summary = "裁剪图片", description = "按指定坐标和尺寸裁剪图片")
    public Result<Map<String, String>> cropImage(
            @Parameter(description = "图片文件") @RequestParam("file") MultipartFile file,
            @Parameter(description = "起始X坐标") @RequestParam int x,
            @Parameter(description = "起始Y坐标") @RequestParam int y,
            @Parameter(description = "裁剪宽度") @RequestParam int width,
            @Parameter(description = "裁剪高度") @RequestParam int height) {
        try {
            // 先保存临时文件
            String tempPath = System.getProperty("java.io.tmpdir") + "/" + 
                    System.currentTimeMillis() + "_" + file.getOriginalFilename();
            file.transferTo(new File(tempPath));
            
            // 执行裁剪
            String croppedPath = imageProcessService.cropImage(
                    new File(tempPath), x, y, width, height);
            
            // 删除临时文件
            new File(tempPath).delete();
            
            Map<String, String> result = new HashMap<>();
            result.put("croppedPath", croppedPath);
            result.put("x", String.valueOf(x));
            result.put("y", String.valueOf(y));
            result.put("width", String.valueOf(width));
            result.put("height", String.valueOf(height));
            
            return Result.success(result);
            
        } catch (Exception e) {
            log.error("图片裁剪失败", e);
            return Result.fail("裁剪失败: " + e.getMessage());
        }
    }

    /**
     * 删除图片
     */
    @DeleteMapping("/{fileId}")
    @Operation(summary = "删除图片", description = "根据文件ID删除图片及其缩略图")
    public Result<Void> deleteImage(
            @Parameter(description = "文件ID") @PathVariable String fileId) {
        try {
            // 删除原图和缩略图
            String[] paths = {
                    "./uploads/images/" + fileId + ".*",
                    "./uploads/thumbnails/" + fileId + "_thumb.*"
            };
            
            for (String pathPattern : paths) {
                String dir = pathPattern.substring(0, pathPattern.lastIndexOf("/"));
                String prefix = fileId;
                
                File directory = new File(dir);
                if (directory.exists()) {
                    File[] files = directory.listFiles((d, name) -> name.startsWith(prefix));
                    if (files != null) {
                        for (File f : files) {
                            imageProcessService.deleteImage(f.getAbsolutePath());
                        }
                    }
                }
            }
            
            return Result.success(null);
            
        } catch (Exception e) {
            log.error("删除图片失败", e);
            return Result.fail("删除失败: " + e.getMessage());
        }
    }
}
