package com.ai.cs.knowledge.service;

import com.ai.cs.knowledge.config.UploadProperties;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 文件上传服务
 * 负责处理知识库文档的上传、临时存储和清理
 */
@Slf4j
@Service
public class FileUploadService {

    private final UploadProperties uploadProperties;

    // 文件魔数映射表（文件头字节 -> 文件类型）
    private static final Map<String, String> FILE_MAGIC_NUMBERS = new HashMap<>();
    
    static {
        // 图片文件
        FILE_MAGIC_NUMBERS.put("FFD8FF", "jpg"); // JPEG
        FILE_MAGIC_NUMBERS.put("89504E47", "png"); // PNG
        FILE_MAGIC_NUMBERS.put("47494638", "gif"); // GIF
        FILE_MAGIC_NUMBERS.put("424D", "bmp"); // BMP
        FILE_MAGIC_NUMBERS.put("52494646", "webp"); // WebP (RIFF)
        
        // 文档文件
        FILE_MAGIC_NUMBERS.put("25504446", "pdf"); // PDF
        FILE_MAGIC_NUMBERS.put("504B0304", "docx"); // ZIP (DOCX/XLSX/PPTX)
        FILE_MAGIC_NUMBERS.put("D0CF11E0", "doc"); // OLE2 (DOC/XLS/PPT)
        
        // 压缩文件
        FILE_MAGIC_NUMBERS.put("504B0304", "zip"); // ZIP
        FILE_MAGIC_NUMBERS.put("377ABCAF", "7z"); // 7Z
        FILE_MAGIC_NUMBERS.put("1F8B", "gz"); // GZIP
        
        // 音频文件
        FILE_MAGIC_NUMBERS.put("494433", "mp3"); // MP3
        FILE_MAGIC_NUMBERS.put("664C6143", "flac"); // FLAC
        FILE_MAGIC_NUMBERS.put("FFF1", "aac"); // AAC
        FILE_MAGIC_NUMBERS.put("52494646", "wav"); // WAV (RIFF)
    }

    /** 临时目录绝对路径（构造时解析；创建失败在首次上传时重试） */
    private volatile Path tempDir;

    public FileUploadService(UploadProperties uploadProperties) {
        this.uploadProperties = uploadProperties;
        try {
            prepareTempDir();
        } catch (IOException e) {
            log.error("上传临时目录初始化失败，将在首次上传时重试: {}", e.getMessage());
        }
    }

    /** 懒初始化临时目录；使用绝对路径，避免 transferTo 按容器临时目录解析相对路径 */
    private Path prepareTempDir() throws IOException {
        Path path = tempDir;
        if (path == null) {
            synchronized (this) {
                if (tempDir == null) {
                    tempDir = Paths.get(uploadProperties.getTempPath()).toAbsolutePath().normalize();
                    Files.createDirectories(tempDir);
                }
                path = tempDir;
            }
        }
        return path;
    }

    /**
     * 保存上传文件到临时目录，返回本地路径
     * @param file 上传的文件
     * @return 文件的绝对路径
     * @throws IOException IO异常
     */
    public String saveTempFile(MultipartFile file) throws IOException {
        // 生成唯一文件名，防止重名覆盖
        String originalName = file.getOriginalFilename();
        if (originalName == null || originalName.isEmpty()) {
            throw new IOException("文件名不能为空");
        }
        
        // 验证文件魔数
        validateFileMagicNumber(file);
        
        // 无扩展名时 lastIndexOf 返回 -1，直接 substring(-1) 会抛越界异常
        int dot = originalName.lastIndexOf('.');
        String suffix = dot >= 0 ? originalName.substring(dot) : "";
        String newFileName = UUID.randomUUID() + suffix;

        Path target = prepareTempDir().resolve(newFileName).normalize();
        if (!target.startsWith(tempDir)) {
            throw new IOException("非法的临时文件路径");
        }
        file.transferTo(target.toFile());
        return target.toAbsolutePath().toString();
    }

    /**
     * 验证文件魔数，防止文件类型伪造
     * @param file 上传的文件
     * @throws IOException 文件类型不匹配时抛出异常
     */
    private void validateFileMagicNumber(MultipartFile file) throws IOException {
        String originalName = file.getOriginalFilename();
        if (originalName == null) {
            throw new IOException("文件名不能为空");
        }
        
        // 无扩展名时取空串，交给魔数校验判定为不支持的类型，而不是把整个文件名当扩展名
        int dot = originalName.lastIndexOf('.');
        String extension = dot >= 0 ? originalName.substring(dot + 1).toLowerCase() : "";
        
        try (InputStream inputStream = file.getInputStream()) {
            byte[] header = new byte[8];
            int bytesRead = inputStream.read(header);
            
            if (bytesRead < 2) {
                throw new IOException("文件太小，无法识别类型");
            }
            
            // 转换为十六进制字符串
            StringBuilder hexBuilder = new StringBuilder();
            for (int i = 0; i < Math.min(bytesRead, 8); i++) {
                hexBuilder.append(String.format("%02X", header[i] & 0xFF));
            }
            
            String fileHex = hexBuilder.toString();
            
            // 检查文件魔数是否匹配扩展名
            boolean isValid = false;
            for (Map.Entry<String, String> entry : FILE_MAGIC_NUMBERS.entrySet()) {
                String magicNumber = entry.getKey();
                String fileType = entry.getValue();
                
                if (fileHex.startsWith(magicNumber)) {
                    if (fileType.equals(extension) || isCompatibleType(extension, fileType)) {
                        isValid = true;
                        break;
                    }
                }
            }
            
            if (!isValid) {
                log.warn("文件魔数验证失败: 文件扩展名={}, 文件头={}", extension, fileHex);
                throw new IOException("文件类型与扩展名不匹配，可能存在安全风险");
            }
        }
    }
    
    /**
     * 检查文件类型是否兼容（如docx和zip都使用ZIP格式）
     */
    private boolean isCompatibleType(String extension, String detectedType) {
        // ZIP格式兼容的文件类型
        if (detectedType.equals("zip")) {
            return extension.equals("docx") || extension.equals("xlsx") || extension.equals("pptx");
        }
        // RIFF格式兼容的文件类型
        if (detectedType.equals("webp") || detectedType.equals("wav")) {
            return extension.equals("webp") || extension.equals("wav");
        }
        return false;
    }

    /**
     * 删除临时文件（用完立刻清理，防止磁盘堆积）
     * @param filePath 文件路径
     */
    public void deleteTempFile(String filePath) {
        try {
            FileUtils.forceDelete(new File(filePath));
        } catch (IOException e) {
            // 记录警告但不抛出异常，避免影响主流程
            log.warn("删除临时文件失败: {}, 错误: {}", filePath, e.getMessage());
        }
    }
}
