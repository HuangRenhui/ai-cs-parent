package com.ai.cs.knowledge.service;

import com.ai.cs.knowledge.config.FileProperties;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.compressors.CompressorInputStream;
import org.apache.commons.compress.compressors.CompressorStreamFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * 文件解压服务
 * 支持ZIP、TAR、TAR.GZ、BZ2、7Z等格式的解压
 * 包含Zip炸弹防护机制（限制解压大小和文件数量）
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
     * 解压上传的压缩文件
     * 自动识别压缩格式并解压到指定目录
     *
     * @param file 上传的压缩文件
     * @return 解压结果（包含解压路径和文件列表）
     * @throws IOException 解压失败
     */
    public DecompressResult decompress(MultipartFile file) throws IOException {
        // 保存上传文件到临时目录
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

        // 生成解压任务ID和输出目录
        String taskId = UUID.randomUUID().toString();
        Path outputDir = Paths.get(fileProperties.getDecompressPath(), taskId);
        Files.createDirectories(outputDir);

        // 保存临时文件
        Path tempFile = Paths.get(fileProperties.getDecompressPath(), taskId + "." + extension);
        file.transferTo(tempFile.toFile());

        try {
            // 根据格式选择解压方法
            List<String> extractedFiles;
            switch (extension) {
                case "zip":
                    extractedFiles = decompressZip(tempFile.toFile(), outputDir.toFile());
                    break;
                case "7z":
                    extractedFiles = decompress7z(tempFile.toFile(), outputDir.toFile());
                    break;
                case "tar":
                    extractedFiles = decompressTar(tempFile.toFile(), outputDir.toFile());
                    break;
                case "gz":
                case "tgz":
                    extractedFiles = decompressTarGz(tempFile.toFile(), outputDir.toFile());
                    break;
                case "bz2":
                    extractedFiles = decompressBz2(tempFile.toFile(), outputDir.toFile());
                    break;
                default:
                    throw new IOException("不支持的压缩格式: " + extension);
            }

            log.info("解压完成: 原始文件={}, 解压文件数={}, 输出目录={}", 
                    originalFilename, extractedFiles.size(), outputDir);

            return new DecompressResult(
                    taskId,
                    originalFilename,
                    outputDir.toString(),
                    extractedFiles,
                    extractedFiles.size(),
                    calculateTotalSize(outputDir.toFile())
            );

        } finally {
            // 清理临时压缩文件
            Files.deleteIfExists(tempFile);
        }
    }

    /**
     * 解压已有的本地压缩文件
     *
     * @param archiveFilePath 压缩文件的本地路径
     * @return 解压结果
     * @throws IOException 解压失败
     */
    public DecompressResult decompressFromFile(String archiveFilePath) throws IOException {
        Path archivePath = Paths.get(archiveFilePath).toAbsolutePath().normalize();
        File archiveFile = archivePath.toFile();

        if (!archiveFile.exists() || !archiveFile.isFile()) {
            throw new IOException("压缩文件不存在: " + archiveFilePath);
        }

        String extension = getFileExtension(archiveFile.getName()).toLowerCase();

        // 生成解压任务ID和输出目录
        String taskId = UUID.randomUUID().toString();
        Path outputDir = Paths.get(fileProperties.getDecompressPath(), taskId);
        Files.createDirectories(outputDir);

        List<String> extractedFiles;
        switch (extension) {
            case "zip":
                extractedFiles = decompressZip(archiveFile, outputDir.toFile());
                break;
            case "7z":
                extractedFiles = decompress7z(archiveFile, outputDir.toFile());
                break;
            case "tar":
                extractedFiles = decompressTar(archiveFile, outputDir.toFile());
                break;
            case "gz":
            case "tgz":
                extractedFiles = decompressTarGz(archiveFile, outputDir.toFile());
                break;
            case "bz2":
                extractedFiles = decompressBz2(archiveFile, outputDir.toFile());
                break;
            default:
                throw new IOException("不支持的压缩格式: " + extension);
        }

        return new DecompressResult(
                taskId,
                archiveFile.getName(),
                outputDir.toString(),
                extractedFiles,
                extractedFiles.size(),
                calculateTotalSize(outputDir.toFile())
        );
    }

    /**
     * 查看压缩文件内容列表（不解压）
     *
     * @param file 压缩文件
     * @return 压缩文件内的条目列表
     * @throws IOException 读取失败
     */
    public List<ArchiveEntryInfo> listArchiveContents(MultipartFile file) throws IOException {
        String originalFilename = file.getOriginalFilename();
        String extension = getFileExtension(originalFilename).toLowerCase();

        // 保存临时文件
        Path tempFile = Paths.get(fileProperties.getDecompressPath(), 
                UUID.randomUUID() + "." + extension);
        file.transferTo(tempFile.toFile());

        try {
            switch (extension) {
                case "zip":
                    return listZipContents(tempFile.toFile());
                case "7z":
                    return list7zContents(tempFile.toFile());
                default:
                    throw new IOException("暂不支持查看该格式的压缩内容: " + extension);
            }
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    // ========== 各格式解压实现 ==========

    /**
     * 解压ZIP格式文件
     */
    private List<String> decompressZip(File zipFile, File outputDir) throws IOException {
        List<String> extractedFiles = new ArrayList<>();
        long maxTotalSize = fileProperties.getMaxDecompressSize() * 1024 * 1024;
        int maxFiles = fileProperties.getMaxDecompressFiles();
        long totalSize = 0;
        int fileCount = 0;

        try (java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(
                new BufferedInputStream(new FileInputStream(zipFile)))) {
            
            java.util.zip.ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                // Zip炸弹防护
                fileCount++;
                if (fileCount > maxFiles) {
                    throw new IOException("解压文件数量超过限制: " + maxFiles);
                }

                File outputFile = newFile(outputDir, entry.getName());

                if (entry.isDirectory()) {
                    outputFile.mkdirs();
                } else {
                    // 确保父目录存在
                    outputFile.getParentFile().mkdirs();

                    try (BufferedOutputStream bos = new BufferedOutputStream(
                            new FileOutputStream(outputFile))) {
                        byte[] buffer = new byte[8192];
                        int bytesRead;
                        while ((bytesRead = zis.read(buffer)) != -1) {
                            bos.write(buffer, 0, bytesRead);
                            totalSize += bytesRead;
                            if (totalSize > maxTotalSize) {
                                throw new IOException("解压总大小超过限制: " + 
                                        fileProperties.getMaxDecompressSize() + "MB");
                            }
                        }
                    }

                    extractedFiles.add(outputFile.getAbsolutePath());
                }
                zis.closeEntry();
            }
        }

        return extractedFiles;
    }

    /**
     * 解压7Z格式文件
     */
    private List<String> decompress7z(File sevenZFile, File outputDir) throws IOException {
        List<String> extractedFiles = new ArrayList<>();
        long maxTotalSize = fileProperties.getMaxDecompressSize() * 1024 * 1024;
        int maxFiles = fileProperties.getMaxDecompressFiles();
        long totalSize = 0;
        int fileCount = 0;

        try (SevenZFile sevenZ = new SevenZFile(sevenZFile)) {
            SevenZArchiveEntry entry;
            while ((entry = sevenZ.getNextEntry()) != null) {
                fileCount++;
                if (fileCount > maxFiles) {
                    throw new IOException("解压文件数量超过限制: " + maxFiles);
                }

                File outputFile = newFile(outputDir, entry.getName());

                if (entry.isDirectory()) {
                    outputFile.mkdirs();
                } else {
                    outputFile.getParentFile().mkdirs();

                    try (BufferedOutputStream bos = new BufferedOutputStream(
                            new FileOutputStream(outputFile))) {
                        byte[] buffer = new byte[8192];
                        int bytesRead;
                        while ((bytesRead = sevenZ.read(buffer)) != -1) {
                            bos.write(buffer, 0, bytesRead);
                            totalSize += bytesRead;
                            if (totalSize > maxTotalSize) {
                                throw new IOException("解压总大小超过限制: " + 
                                        fileProperties.getMaxDecompressSize() + "MB");
                            }
                        }
                    }

                    extractedFiles.add(outputFile.getAbsolutePath());
                }
            }
        }

        return extractedFiles;
    }

    /**
     * 解压TAR格式文件
     */
    private List<String> decompressTar(File tarFile, File outputDir) throws IOException {
        List<String> extractedFiles = new ArrayList<>();
        long maxTotalSize = fileProperties.getMaxDecompressSize() * 1024 * 1024;
        int maxFiles = fileProperties.getMaxDecompressFiles();
        long totalSize = 0;
        int fileCount = 0;

        try (ArchiveInputStream<?> ais = new ArchiveStreamFactory()
                .createArchiveInputStream(ArchiveStreamFactory.TAR,
                        new BufferedInputStream(new FileInputStream(tarFile)))) {

            ArchiveEntry entry;
            while ((entry = ais.getNextEntry()) != null) {
                fileCount++;
                if (fileCount > maxFiles) {
                    throw new IOException("解压文件数量超过限制: " + maxFiles);
                }

                File outputFile = newFile(outputDir, entry.getName());

                if (entry.isDirectory()) {
                    outputFile.mkdirs();
                } else {
                    outputFile.getParentFile().mkdirs();

                    try (BufferedOutputStream bos = new BufferedOutputStream(
                            new FileOutputStream(outputFile))) {
                        byte[] buffer = new byte[8192];
                        int bytesRead;
                        while ((bytesRead = ais.read(buffer)) != -1) {
                            bos.write(buffer, 0, bytesRead);
                            totalSize += bytesRead;
                            if (totalSize > maxTotalSize) {
                                throw new IOException("解压总大小超过限制: " + 
                                        fileProperties.getMaxDecompressSize() + "MB");
                            }
                        }
                    }

                    extractedFiles.add(outputFile.getAbsolutePath());
                }
            }
        } catch (org.apache.commons.compress.archivers.ArchiveException e) {
            throw new IOException("TAR解压失败: " + e.getMessage(), e);
        }

        return extractedFiles;
    }

    /**
     * 解压TAR.GZ / TGZ格式文件
     */
    private List<String> decompressTarGz(File tarGzFile, File outputDir) throws IOException {
        List<String> extractedFiles = new ArrayList<>();
        long maxTotalSize = fileProperties.getMaxDecompressSize() * 1024 * 1024;
        int maxFiles = fileProperties.getMaxDecompressFiles();
        long totalSize = 0;
        int fileCount = 0;

        try (CompressorInputStream cis = new CompressorStreamFactory()
                .createCompressorInputStream(CompressorStreamFactory.GZIP,
                        new BufferedInputStream(new FileInputStream(tarGzFile)));
             ArchiveInputStream<?> ais = new ArchiveStreamFactory()
                .createArchiveInputStream(ArchiveStreamFactory.TAR, cis)) {

            ArchiveEntry entry;
            while ((entry = ais.getNextEntry()) != null) {
                fileCount++;
                if (fileCount > maxFiles) {
                    throw new IOException("解压文件数量超过限制: " + maxFiles);
                }

                File outputFile = newFile(outputDir, entry.getName());

                if (entry.isDirectory()) {
                    outputFile.mkdirs();
                } else {
                    outputFile.getParentFile().mkdirs();

                    try (BufferedOutputStream bos = new BufferedOutputStream(
                            new FileOutputStream(outputFile))) {
                        byte[] buffer = new byte[8192];
                        int bytesRead;
                        while ((bytesRead = ais.read(buffer)) != -1) {
                            bos.write(buffer, 0, bytesRead);
                            totalSize += bytesRead;
                            if (totalSize > maxTotalSize) {
                                throw new IOException("解压总大小超过限制: " + 
                                        fileProperties.getMaxDecompressSize() + "MB");
                            }
                        }
                    }

                    extractedFiles.add(outputFile.getAbsolutePath());
                }
            }
        } catch (org.apache.commons.compress.compressors.CompressorException | 
                 org.apache.commons.compress.archivers.ArchiveException e) {
            throw new IOException("TAR.GZ解压失败: " + e.getMessage(), e);
        }

        return extractedFiles;
    }

    /**
     * 解压BZ2格式文件（单文件压缩）
     */
    private List<String> decompressBz2(File bz2File, File outputDir) throws IOException {
        List<String> extractedFiles = new ArrayList<>();
        long maxTotalSize = fileProperties.getMaxDecompressSize() * 1024 * 1024;
        long totalSize = 0;

        // BZ2是单文件压缩格式，解压后去掉.bz2后缀
        String outputName = bz2File.getName();
        if (outputName.endsWith(".bz2")) {
            outputName = outputName.substring(0, outputName.length() - 4);
        }

        File outputFile = newFile(outputDir, outputName);

        try (CompressorInputStream cis = new CompressorStreamFactory()
                .createCompressorInputStream(CompressorStreamFactory.BZIP2,
                        new BufferedInputStream(new FileInputStream(bz2File)));
             BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(outputFile))) {

            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = cis.read(buffer)) != -1) {
                bos.write(buffer, 0, bytesRead);
                totalSize += bytesRead;
                if (totalSize > maxTotalSize) {
                    throw new IOException("解压总大小超过限制: " + 
                            fileProperties.getMaxDecompressSize() + "MB");
                }
            }
        } catch (org.apache.commons.compress.compressors.CompressorException e) {
            throw new IOException("BZ2解压失败: " + e.getMessage(), e);
        }

        extractedFiles.add(outputFile.getAbsolutePath());
        return extractedFiles;
    }

    // ========== 压缩文件内容列表 ==========

    /**
     * 查看ZIP压缩文件内容
     */
    private List<ArchiveEntryInfo> listZipContents(File zipFile) throws IOException {
        List<ArchiveEntryInfo> entries = new ArrayList<>();

        try (java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(
                new BufferedInputStream(new FileInputStream(zipFile)))) {
            
            java.util.zip.ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                entries.add(new ArchiveEntryInfo(
                        entry.getName(),
                        entry.isDirectory(),
                        entry.isDirectory() ? 0 : entry.getSize(),
                        entry.isDirectory() ? 0 : entry.getCompressedSize()
                ));
                zis.closeEntry();
            }
        }

        return entries;
    }

    /**
     * 查看7Z压缩文件内容
     */
    private List<ArchiveEntryInfo> list7zContents(File sevenZFile) throws IOException {
        List<ArchiveEntryInfo> entries = new ArrayList<>();

        try (SevenZFile sevenZ = new SevenZFile(sevenZFile)) {
            SevenZArchiveEntry entry;
            while ((entry = sevenZ.getNextEntry()) != null) {
                entries.add(new ArchiveEntryInfo(
                        entry.getName(),
                        entry.isDirectory(),
                        entry.isDirectory() ? 0 : entry.getSize(),
                        0 // 7z不直接提供单文件压缩大小
                ));
            }
        }

        return entries;
    }

    // ========== 工具方法 ==========

    /**
     * 安全创建输出文件（防止Zip Slip路径穿越攻击）
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

    /**
     * 计算目录总大小
     */
    private long calculateTotalSize(File dir) {
        long totalSize = 0;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    totalSize += file.length();
                } else if (file.isDirectory()) {
                    totalSize += calculateTotalSize(file);
                }
            }
        }
        return totalSize;
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
