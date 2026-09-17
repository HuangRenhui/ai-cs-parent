package com.ai.cs.knowledge.controller;

import com.ai.cs.common.result.Result;
import com.ai.cs.knowledge.config.FileProperties;
import com.ai.cs.knowledge.service.FileDecompressService;
import com.ai.cs.knowledge.service.FileDownloadService;
import com.ai.cs.knowledge.service.FilePreviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * 文件管理控制器
 * 提供文件预览、下载（含断点续传）、解压、批量打包等功能
 */
@Slf4j
@RestController
@RequestMapping("/api/file")
@Tag(name = "文件管理", description = "文件查看、下载与解压接口")
public class FileController {

    private final FilePreviewService filePreviewService;
    private final FileDownloadService fileDownloadService;
    private final FileDecompressService fileDecompressService;
    private final FileProperties fileProperties;

    public FileController(FilePreviewService filePreviewService,
                          FileDownloadService fileDownloadService,
                          FileDecompressService fileDecompressService,
                          FileProperties fileProperties) {
        this.filePreviewService = filePreviewService;
        this.fileDownloadService = fileDownloadService;
        this.fileDecompressService = fileDecompressService;
        this.fileProperties = fileProperties;
    }

    // ========== 文件预览 ==========

    /**
     * 预览文件（浏览器内联展示）
     * 支持图片、PDF、音频、视频等浏览器可渲染的类型
     */
    @GetMapping("/preview")
    @Operation(summary = "文件预览", description = "在浏览器中直接展示文件内容（支持图片/PDF/音频/视频）")
    public ResponseEntity<Resource> preview(
            @Parameter(description = "文件路径（相对于rootPath或绝对路径）") @RequestParam String filePath) {
        try {
            return filePreviewService.preview(filePath);
        } catch (IOException e) {
            log.error("文件预览失败: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * 按fileId预览已上传的文件
     * 用于预览图片存储目录、音频存储目录中的文件
     */
    @GetMapping("/preview/{storageType}/{fileId}")
    @Operation(summary = "按ID预览文件", description = "根据存储类型和文件ID预览已上传的文件")
    public ResponseEntity<Resource> previewByFileId(
            @Parameter(description = "存储类型: images/audios/documents") @PathVariable String storageType,
            @Parameter(description = "文件ID") @PathVariable String fileId) {
        try {
            String storageDir = resolveStorageDir(storageType);
            return filePreviewService.previewByFileId(storageDir, fileId);
        } catch (IOException e) {
            log.error("文件预览失败: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    // ========== 文件信息 ==========

    /**
     * 获取文件元信息
     */
    @GetMapping("/info")
    @Operation(summary = "获取文件信息", description = "获取文件的大小、类型、是否可预览等元数据信息")
    public Result<Map<String, Object>> getFileInfo(
            @Parameter(description = "文件路径") @RequestParam String filePath) {
        try {
            Map<String, Object> info = filePreviewService.getFileInfo(filePath);
            return Result.success(info);
        } catch (IOException e) {
            log.error("获取文件信息失败: {}", e.getMessage());
            return Result.fail("获取文件信息失败: " + e.getMessage());
        }
    }

    /**
     * 列出目录下的文件
     */
    @GetMapping("/list")
    @Operation(summary = "列出文件列表", description = "列出指定目录下的文件，支持递归列出子目录")
    public Result<List<Map<String, Object>>> listFiles(
            @Parameter(description = "目录路径") @RequestParam(defaultValue = "./uploads") String dirPath,
            @Parameter(description = "是否递归列出子目录") @RequestParam(defaultValue = "false") boolean recursive) {
        try {
            List<Map<String, Object>> files = filePreviewService.listFiles(dirPath, recursive);
            return Result.success(files);
        } catch (IOException e) {
            log.error("列出文件失败: {}", e.getMessage());
            return Result.fail("列出文件失败: " + e.getMessage());
        }
    }

    // ========== 文件下载 ==========

    /**
     * 标准文件下载
     */
    @GetMapping("/download")
    @Operation(summary = "文件下载", description = "标准文件下载，支持Content-Disposition: attachment")
    public ResponseEntity<Resource> download(
            @Parameter(description = "文件路径") @RequestParam String filePath) {
        try {
            return fileDownloadService.download(filePath);
        } catch (IOException e) {
            log.error("文件下载失败: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * 断点续传下载（支持HTTP Range）
     */
    @GetMapping("/download/range")
    @Operation(summary = "断点续传下载", description = "支持HTTP Range请求头实现断点续传下载")
    public ResponseEntity<Resource> downloadWithRange(
            @Parameter(description = "文件路径") @RequestParam String filePath,
            @RequestHeader(value = "Range", required = false) String rangeHeader) {
        try {
            return fileDownloadService.downloadWithRange(filePath, rangeHeader);
        } catch (IOException e) {
            log.error("断点续传下载失败: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * 批量文件打包下载（生成ZIP）
     */
    @PostMapping("/download/batch")
    @Operation(summary = "批量打包下载", description = "将多个文件打包成ZIP后下载")
    public ResponseEntity<Resource> batchDownload(
            @Parameter(description = "要打包的文件路径列表") @RequestBody List<String> filePaths,
            @Parameter(description = "ZIP文件名（不含扩展名）") @RequestParam(required = false) String zipFileName) {
        try {
            return fileDownloadService.batchDownload(filePaths, zipFileName);
        } catch (IOException e) {
            log.error("批量打包下载失败: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    // ========== 文件解压 ==========

    /**
     * 上传并解压压缩文件
     */
    @PostMapping("/decompress")
    @Operation(summary = "上传并解压", description = "上传压缩文件并自动解压（支持ZIP/7Z/TAR/GZ/BZ2/TGZ）")
    public Result<FileDecompressService.DecompressResult> decompress(
            @Parameter(description = "压缩文件") @RequestParam("file") MultipartFile file) {
        try {
            FileDecompressService.DecompressResult result = fileDecompressService.decompress(file);
            return Result.success(result);
        } catch (IOException e) {
            log.error("解压失败: {}", e.getMessage());
            return Result.fail("解压失败: " + e.getMessage());
        }
    }

    /**
     * 解压本地已有的压缩文件
     */
    @PostMapping("/decompress/local")
    @Operation(summary = "解压本地文件", description = "解压本地已有的压缩文件")
    public Result<FileDecompressService.DecompressResult> decompressLocalFile(
            @Parameter(description = "本地压缩文件路径") @RequestParam String archiveFilePath) {
        try {
            FileDecompressService.DecompressResult result = fileDecompressService.decompressFromFile(archiveFilePath);
            return Result.success(result);
        } catch (IOException e) {
            log.error("解压本地文件失败: {}", e.getMessage());
            return Result.fail("解压失败: " + e.getMessage());
        }
    }

    /**
     * 查看压缩文件内容（不解压）
     */
    @PostMapping("/decompress/contents")
    @Operation(summary = "查看压缩内容", description = "查看压缩文件内的文件列表（不解压）")
    public Result<List<FileDecompressService.ArchiveEntryInfo>> listArchiveContents(
            @Parameter(description = "压缩文件") @RequestParam("file") MultipartFile file) {
        try {
            List<FileDecompressService.ArchiveEntryInfo> entries = fileDecompressService.listArchiveContents(file);
            return Result.success(entries);
        } catch (IOException e) {
            log.error("查看压缩内容失败: {}", e.getMessage());
            return Result.fail("查看失败: " + e.getMessage());
        }
    }

    /**
     * 预览解压后的文件
     */
    @GetMapping("/decompressed/preview/{taskId}")
    @Operation(summary = "预览解压文件", description = "预览解压后的文件")
    public ResponseEntity<Resource> previewDecompressed(
            @Parameter(description = "解压任务ID") @PathVariable String taskId,
            @Parameter(description = "子文件路径") @RequestParam(required = false) String subPath) {
        try {
            String filePath = fileProperties.getDecompressPath() + "/" + taskId;
            if (subPath != null && !subPath.isEmpty()) {
                filePath += "/" + subPath;
            }
            return filePreviewService.preview(filePath);
        } catch (IOException e) {
            log.error("预览解压文件失败: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * 列出解压后的文件
     */
    @GetMapping("/decompressed/list/{taskId}")
    @Operation(summary = "列出解压文件", description = "列出解压任务产出的文件列表")
    public Result<List<Map<String, Object>>> listDecompressedFiles(
            @Parameter(description = "解压任务ID") @PathVariable String taskId) {
        try {
            String dirPath = fileProperties.getDecompressPath() + "/" + taskId;
            List<Map<String, Object>> files = filePreviewService.listFiles(dirPath, true);
            return Result.success(files);
        } catch (IOException e) {
            log.error("列出解压文件失败: {}", e.getMessage());
            return Result.fail("列出文件失败: " + e.getMessage());
        }
    }

    // ========== 工具方法 ==========

    /**
     * 根据存储类型解析存储目录
     */
    private String resolveStorageDir(String storageType) {
        switch (storageType.toLowerCase()) {
            case "images":
                return fileProperties.getRootPath() + "/images";
            case "audios":
                return fileProperties.getRootPath() + "/audios";
            case "documents":
                return fileProperties.getRootPath() + "/documents";
            case "thumbnails":
                return fileProperties.getRootPath() + "/thumbnails";
            default:
                return fileProperties.getRootPath() + "/" + storageType;
        }
    }
}
