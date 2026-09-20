package com.ai.cs.base.service;

import com.ai.cs.common.dto.AttachmentDTO;
import com.ai.cs.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.UUID;

/**
 * 对话附件存储服务：负责 C 端对话附件的校验、落盘与元信息组装。
 *
 * <p><b>当前阶段定位（A 方案）</b>：附件只做「上传 + 展示 + 随消息落库」，AI 不解析内容。
 * 结构上已为多模态（B 方案）预留 {@code extractedText / parseStatus} 字段，
 * 待模型层（ModelRouter / MultimodalChatService）打通后，在此处补充解析逻辑即可，
 * 无需改动 DTO、Controller 与前端。</p>
 *
 * <p><b>安全策略</b>：不做扩展名白名单限制（允许全部类型上传，符合「全类型可选」需求），
 * 但通过三重防护兜底：① 单文件大小上限；② 文件名 UUID 化不落原文件名；
 * ③ 路径 normalize 后强制校验仍在存储目录内，防御目录穿越。</p>
 *
 * @author huangrenhui
 * @date 2026/9/21
 */
@Service
public class ChatAttachmentStorageService {

    /** 附件大小上限：20MB（对话场景足够，避免大文件拖垮磁盘与内存） */
    private static final long MAX_SIZE = 20 * 1024 * 1024L;

    /** 附件访问 URL 前缀 */
    private static final String URL_PREFIX = "/files/chat/";

    /** 附件存储根目录（默认 uploads/chat，与 FileWebConfig 的 /files/chat/** 映射对应） */
    @Value("${app.chat-upload-dir:uploads/chat}")
    private String chatUploadDir;

    /** 附件存储目录绝对路径 */
    private Path storagePath;

    /**
     * 启动初始化：解析存储目录为绝对路径并确保目录存在
     */
    @PostConstruct
    public void init() throws IOException {
        storagePath = Paths.get(chatUploadDir).toAbsolutePath().normalize();
        Files.createDirectories(storagePath);
    }

    /**
     * 保存对话附件并返回附件元信息。
     *
     * @param file 上传的文件（允许任意类型）
     * @return 附件元信息，含访问 URL、分类、大小等
     */
    public AttachmentDTO save(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择要上传的附件");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new BusinessException("附件不能超过 20MB");
        }

        String originalName = file.getOriginalFilename();
        if (originalName == null || originalName.isBlank()) {
            originalName = "未命名文件";
        }

        String ext = extractExtension(originalName);
        // UUID 命名：避免文件名冲突，同时防止原文件名携带路径/特殊字符引发注入
        String storedName = UUID.randomUUID().toString().replace("-", "") + ext;
        Path target = storagePath.resolve(storedName).normalize();
        // 防御目录穿越：normalize 后必须仍位于存储目录内
        if (!target.startsWith(storagePath)) {
            throw new BusinessException("非法文件路径");
        }

        try {
            file.transferTo(target);
        } catch (IOException e) {
            throw new BusinessException("附件保存失败，请稍后重试");
        }

        AttachmentDTO dto = new AttachmentDTO();
        dto.setFileId(storedName.substring(0, storedName.length() - ext.length()));
        dto.setUrl(URL_PREFIX + storedName);
        dto.setFileName(originalName);
        dto.setFileSize(file.getSize());
        dto.setContentType(file.getContentType());
        dto.setCategory(resolveCategory(ext, file.getContentType()));
        // A 方案：暂不解析内容，状态置 0（未解析），前端据此提示「AI 暂不支持读取」
        dto.setParseStatus(0);
        return dto;
    }

    /**
     * 根据文件名解析扩展名（含前导点，统一小写）。
     * <p>无扩展名时返回空串。</p>
     */
    private String extractExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot).toLowerCase(Locale.ROOT);
    }

    /**
     * 判定附件大类，供前端选择渲染方式。
     * <p>优先按扩展名判定，扩展名缺失时回退到 MIME 类型。</p>
     */
    private String resolveCategory(String ext, String contentType) {
        if (isImage(ext)) {
            return "image";
        }
        if (isAudio(ext)) {
            return "audio";
        }
        if (isVideo(ext)) {
            return "video";
        }
        if (isArchive(ext)) {
            return "archive";
        }
        if (isDocument(ext)) {
            return "document";
        }
        // 扩展名无法判定时，回退到 MIME 大类
        if (contentType != null) {
            if (contentType.startsWith("image/")) {
                return "image";
            }
            if (contentType.startsWith("audio/")) {
                return "audio";
            }
            if (contentType.startsWith("video/")) {
                return "video";
            }
            if (contentType.startsWith("text/")) {
                return "document";
            }
        }
        return "other";
    }

    private boolean isImage(String ext) {
        return switch (ext) {
            case ".jpg", ".jpeg", ".png", ".gif", ".webp", ".bmp", ".svg", ".ico", ".heic" -> true;
            default -> false;
        };
    }

    private boolean isAudio(String ext) {
        return switch (ext) {
            case ".mp3", ".wav", ".amr", ".m4a", ".aac", ".ogg", ".flac", ".silk" -> true;
            default -> false;
        };
    }

    private boolean isVideo(String ext) {
        return switch (ext) {
            case ".mp4", ".mov", ".avi", ".mkv", ".webm", ".flv" -> true;
            default -> false;
        };
    }

    private boolean isArchive(String ext) {
        return switch (ext) {
            case ".zip", ".rar", ".7z", ".tar", ".gz" -> true;
            default -> false;
        };
    }

    private boolean isDocument(String ext) {
        return switch (ext) {
            case ".pdf", ".doc", ".docx", ".xls", ".xlsx", ".ppt", ".pptx",
                 ".txt", ".md", ".csv", ".json", ".xml", ".log", ".yaml", ".yml" -> true;
            default -> false;
        };
    }
}
