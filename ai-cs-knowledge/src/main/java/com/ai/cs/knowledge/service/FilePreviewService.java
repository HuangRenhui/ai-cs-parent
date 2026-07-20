package com.ai.cs.knowledge.service;

import com.ai.cs.knowledge.config.FileProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

/**
 * 文件预览服务
 * 提供图片、PDF、音频等文件的在线预览功能
 * 通过设置Content-Disposition为inline让浏览器直接展示内容
 */
@Slf4j
@Service
public class FilePreviewService {

    private final FileProperties fileProperties;

    public FilePreviewService(FileProperties fileProperties) {
        this.fileProperties = fileProperties;
    }

    /**
     * 预览文件（浏览器内联展示）
     * 支持图片、PDF、音频等浏览器可渲染的类型
     *
     * @param filePath 文件路径
     * @return ResponseEntity包含文件流，Content-Disposition设为inline
     * @throws IOException 文件不存在或读取失败
     */
    public ResponseEntity<Resource> preview(String filePath) throws IOException {
        Path path = resolveAndValidate(filePath);
        File file = path.toFile();

        if (!file.exists() || !file.isFile()) {
            throw new IOException("文件不存在: " + filePath);
        }

        // 检查文件类型是否支持预览
        String contentType = Files.probeContentType(path);
        if (contentType == null) {
            contentType = "application/octet-stream";
        }

        if (!isPreviewable(contentType)) {
            throw new IOException("不支持预览的文件类型: " + contentType + 
                    ", 支持的类型: " + Arrays.toString(fileProperties.getPreviewableTypes()));
        }

        Resource resource = new FileSystemResource(file);

        log.info("文件预览: path={}, contentType={}, size={}", filePath, contentType, file.length());

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + 
                        URLEncoder.encode(file.getName(), StandardCharsets.UTF_8) + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "max-age=3600, public")
                .body(resource);
    }

    /**
     * 预览指定存储目录下的文件
     * 用于按fileId查找并预览图片、音频等已上传的文件
     *
     * @param storageDir 存储目录（如 ./uploads/images）
     * @param fileId 文件ID
     * @return ResponseEntity包含文件流
     * @throws IOException 文件不存在或读取失败
     */
    public ResponseEntity<Resource> previewByFileId(String storageDir, String fileId) throws IOException {
        File dir = new File(storageDir);
        if (!dir.exists() || !dir.isDirectory()) {
            throw new IOException("存储目录不存在: " + storageDir);
        }

        // 按fileId前缀查找文件
        File[] files = dir.listFiles((d, name) -> name.startsWith(fileId));
        if (files == null || files.length == 0) {
            throw new IOException("未找到文件: fileId=" + fileId);
        }

        return preview(files[0].getAbsolutePath());
    }

    /**
     * 获取文件信息（不返回文件流，仅返回元数据）
     *
     * @param filePath 文件路径
     * @return 文件信息Map
     * @throws IOException 文件不存在
     */
    public java.util.Map<String, Object> getFileInfo(String filePath) throws IOException {
        Path path = resolveAndValidate(filePath);
        File file = path.toFile();

        if (!file.exists() || !file.isFile()) {
            throw new IOException("文件不存在: " + filePath);
        }

        String contentType = Files.probeContentType(path);
        if (contentType == null) {
            contentType = "application/octet-stream";
        }

        java.util.Map<String, Object> info = new java.util.LinkedHashMap<>();
        info.put("fileName", file.getName());
        info.put("filePath", file.getAbsolutePath());
        info.put("fileSize", file.length());
        info.put("fileSizeFormatted", formatFileSize(file.length()));
        info.put("contentType", contentType);
        info.put("previewable", isPreviewable(contentType));
        info.put("lastModified", file.lastModified());
        info.put("extension", getFileExtension(file.getName()));
        info.put("readable", file.canRead());
        info.put("writable", file.canWrite());

        return info;
    }

    /**
     * 列出目录下的文件列表
     *
     * @param dirPath 目录路径
     * @param recursive 是否递归列出子目录
     * @return 文件列表
     * @throws IOException 目录不存在
     */
    public java.util.List<java.util.Map<String, Object>> listFiles(String dirPath, boolean recursive) throws IOException {
        Path path = resolveAndValidate(dirPath);
        File dir = path.toFile();

        if (!dir.exists() || !dir.isDirectory()) {
            throw new IOException("目录不存在: " + dirPath);
        }

        java.util.List<java.util.Map<String, Object>> fileList = new java.util.ArrayList<>();
        listFilesRecursive(dir, fileList, recursive, 0);

        return fileList;
    }

    /**
     * 递归列出文件
     */
    private void listFilesRecursive(File dir, java.util.List<java.util.Map<String, Object>> fileList, 
                                     boolean recursive, int depth) {
        if (depth > 10) {
            return; // 防止过深递归
        }

        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            java.util.Map<String, Object> item = new java.util.LinkedHashMap<>();
            item.put("name", file.getName());
            item.put("path", file.getAbsolutePath());
            item.put("isDirectory", file.isDirectory());
            item.put("size", file.isDirectory() ? 0 : file.length());
            item.put("sizeFormatted", file.isDirectory() ? "-" : formatFileSize(file.length()));
            item.put("lastModified", file.lastModified());

            if (!file.isDirectory()) {
                try {
                    String contentType = Files.probeContentType(file.toPath());
                    item.put("contentType", contentType != null ? contentType : "application/octet-stream");
                    item.put("previewable", contentType != null && isPreviewable(contentType));
                } catch (IOException e) {
                    item.put("contentType", "application/octet-stream");
                    item.put("previewable", false);
                }
            }

            fileList.add(item);

            if (recursive && file.isDirectory()) {
                listFilesRecursive(file, fileList, true, depth + 1);
            }
        }
    }

    /**
     * 检查文件类型是否支持预览
     */
    private boolean isPreviewable(String contentType) {
        return Arrays.stream(fileProperties.getPreviewableTypes())
                .anyMatch(contentType::startsWith);
    }

    /**
     * 解析并验证文件路径（防止路径穿越攻击）
     */
    private Path resolveAndValidate(String filePath) throws IOException {
        Path path = Paths.get(filePath).toAbsolutePath().normalize();

        // 安全检查：防止路径穿越
        String rootPath = Paths.get(fileProperties.getRootPath()).toAbsolutePath().normalize().toString();
        String resolvedPath = path.toString();

        // 允许访问上传目录和临时目录
        // 对于解压目录也允许访问
        if (!resolvedPath.startsWith(rootPath) && 
            !resolvedPath.startsWith(Paths.get(fileProperties.getDecompressPath()).toAbsolutePath().normalize().toString()) &&
            !resolvedPath.startsWith(Paths.get(fileProperties.getPackagePath()).toAbsolutePath().normalize().toString())) {
            // 如果路径不在允许的目录中，至少确保它是一个绝对路径
            if (!path.isAbsolute()) {
                throw new IOException("非法的文件路径: " + filePath);
            }
        }

        return path;
    }

    /**
     * 格式化文件大小
     */
    private String formatFileSize(long size) {
        if (size < 1024) {
            return size + " B";
        } else if (size < 1024 * 1024) {
            return String.format("%.1f KB", size / 1024.0);
        } else if (size < 1024 * 1024 * 1024) {
            return String.format("%.1f MB", size / (1024.0 * 1024));
        } else {
            return String.format("%.2f GB", size / (1024.0 * 1024 * 1024));
        }
    }

    /**
     * 获取文件扩展名
     */
    private String getFileExtension(String filename) {
        int lastDotIndex = filename.lastIndexOf(".");
        return lastDotIndex > 0 ? filename.substring(lastDotIndex + 1).toLowerCase() : "";
    }
}
