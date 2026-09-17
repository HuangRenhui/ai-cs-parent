package com.ai.cs.knowledge.service;

import com.ai.cs.knowledge.config.FileProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

/**
 * 文件解压服务（占位）
 *
 * <p>TODO 后续实现：按格式分派解压（ZIP / 7Z / TAR / TAR.GZ / BZ2 / TGZ）、
 * 查看压缩包条目列表；解压时须保留两项安全防护——**Zip Slip 路径穿越校验**
 * （见 {@link #newFile}，已保留）与 **Zip 炸弹防护**
 * （解压文件数与解压总大小按 {@code FileProperties#getMaxDecompressFiles()} /
 * {@code #getMaxDecompressSize()} 上限拦截）。</p>
 *
 * <p>当前不解压、不读条目：三个公开入口在完成「文件名/格式白名单」校验后
 * 一律抛 {@link IOException}（与服务不可用语义一致），不再真正读写压缩包。</p>
 *
 * <p>两个公开嵌套结果类型（{@link DecompressResult}、{@link ArchiveEntryInfo}）
 * 与 Zip Slip 守卫（{@code newFile}）为安全机制。</p>
 */
@Slf4j
@Service
public class FileDecompressService {

    private final FileProperties fileProperties;

    public FileDecompressService(FileProperties fileProperties) {
        this.fileProperties = fileProperties;
        // 初始化解压输出目录
        try {
            Files.createDirectories(Paths.get(fileProperties.getDecompressPath()));
        } catch (IOException e) {
            log.error("初始化解压目录失败", e);
        }
    }

    /**
     * 解压上传的压缩文件（占位：校验后抛 IOException）
     *
     * @param file 上传的压缩文件
     * @return 不返回（抛异常）
     * @throws IOException 占位实现
     */
    public DecompressResult decompress(MultipartFile file) throws IOException {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.contains(".")) {
            throw new IOException("无效的文件名");
        }

        String extension = getFileExtension(originalFilename).toLowerCase();

        // 验证格式
        if (!isAllowedArchiveFormat(extension)) {
            throw new IOException("不支持的压缩格式: " + extension + 
                    ", 支持的格式: " + Arrays.toString(fileProperties.getAllowedArchiveFormats()));
        }

        log.warn("[占位] 压缩包解压未实现 ext={}", extension);
        throw new IOException("压缩包解压为占位实现，后端未接入解压处理");
    }

    /**
     * 解压已有的本地压缩文件（占位：校验后抛 IOException）
     *
     * @param archiveFilePath 压缩文件的本地路径
     * @return 不返回（抛异常）
     * @throws IOException 占位实现
     */
    public DecompressResult decompressFromFile(String archiveFilePath) throws IOException {
        Path archivePath = Paths.get(archiveFilePath).toAbsolutePath().normalize();
        File archiveFile = archivePath.toFile();

        if (!archiveFile.exists() || !archiveFile.isFile()) {
            throw new IOException("压缩文件不存在: " + archiveFilePath);
        }

        log.warn("[占位] 本地压缩包解压未实现 file={}", archiveFile.getName());
        throw new IOException("压缩包解压为占位实现，后端未接入解压处理");
    }

    /**
     * 查看压缩文件内容列表（占位：校验后抛 IOException）
     *
     * @param file 压缩文件
     * @return 不返回（抛异常）
     * @throws IOException 占位实现
     */
    public List<ArchiveEntryInfo> listArchiveContents(MultipartFile file) throws IOException {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.contains(".")) {
            throw new IOException("无效的文件名");
        }

        String extension = getFileExtension(originalFilename).toLowerCase();

        log.warn("[占位] 压缩包内容查看未实现 ext={}", extension);
        throw new IOException("压缩包内容查看为占位实现，后端未接入解压处理");
    }

    // ========== 安全守卫（保留） ==========

    /**
     * 安全创建输出文件（防止Zip Slip路径穿越攻击）
     *
     * <p><b>安全约定</b>：该守卫随解压实现一起保留，恢复解压时**必须**逐条目调用，
     * 拒绝解压到目标目录之外；未实现期间不参与调用链。</p>
     */
    private File newFile(File destinationDir, String entryName) throws IOException {
        File destFile = new File(destinationDir, entryName);
        String destDirPath = destinationDir.getCanonicalPath();
        String destFilePath = destFile.getCanonicalPath();

        if (!destFilePath.startsWith(destDirPath + File.separator) && 
            !destFilePath.equals(destDirPath)) {
            throw new IOException("检测到Zip Slip攻击: 条目在目标目录之外: " + entryName);
        }

        return destFile;
    }

    /**
     * 检查是否为允许的压缩格式
     */
    private boolean isAllowedArchiveFormat(String extension) {
        return Arrays.stream(fileProperties.getAllowedArchiveFormats())
                .anyMatch(format -> format.equalsIgnoreCase(extension));
    }

    /**
     * 获取文件扩展名
     */
    private String getFileExtension(String filename) {
        int lastDotIndex = filename.lastIndexOf(".");
        return lastDotIndex > 0 ? filename.substring(lastDotIndex + 1) : "";
    }

    // ========== 内部DTO类 ==========

    /**
     * 解压结果DTO
     */
    public static class DecompressResult {
        /** 解压任务ID（UUID，同时作为输出子目录名） */
        private final String taskId;
        /** 原始压缩文件名 */
        private final String originalFilename;
        /** 解压输出目录的绝对路径 */
        private final String outputDirectory;
        /** 解压出的文件绝对路径列表（不含目录） */
        private final List<String> extractedFiles;
        /** 解压出的文件数量 */
        private final int fileCount;
        /** 解压后文件总大小（字节） */
        private final long totalSize;

        public DecompressResult(String taskId, String originalFilename, String outputDirectory,
                                List<String> extractedFiles, int fileCount, long totalSize) {
            this.taskId = taskId;
            this.originalFilename = originalFilename;
            this.outputDirectory = outputDirectory;
            this.extractedFiles = extractedFiles;
            this.fileCount = fileCount;
            this.totalSize = totalSize;
        }

        public String getTaskId() { return taskId; }
        public String getOriginalFilename() { return originalFilename; }
        public String getOutputDirectory() { return outputDirectory; }
        public List<String> getExtractedFiles() { return extractedFiles; }
        public int getFileCount() { return fileCount; }
        public long getTotalSize() { return totalSize; }
    }

    /**
     * 压缩包条目信息DTO
     */
    public static class ArchiveEntryInfo {
        /** 条目名称（压缩包内相对路径） */
        private final String name;
        /** 是否为目录条目 */
        private final boolean directory;
        /** 解压后大小（字节，目录为0） */
        private final long size;
        /** 压缩后大小（字节，目录为0） */
        private final long compressedSize;

        public ArchiveEntryInfo(String name, boolean directory, long size, long compressedSize) {
            this.name = name;
            this.directory = directory;
            this.size = size;
            this.compressedSize = compressedSize;
        }

        public String getName() { return name; }
        public boolean isDirectory() { return directory; }
        public long getSize() { return size; }
        public long getCompressedSize() { return compressedSize; }
    }
}
