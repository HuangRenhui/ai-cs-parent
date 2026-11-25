package com.ai.cs.knowledge.service;

import com.ai.cs.knowledge.config.FileProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 文件下载服务
 * 提供标准下载、断点续传（HTTP Range）、批量打包下载等功能
 */
@Slf4j
@Service
public class FileDownloadService {

    /** 批量下载最多允许的文件数，防止一次请求打满磁盘/内存 */
    private static final int MAX_BATCH_FILES = 100;
    /** 批量下载总字节上限（500MB） */
    private static final long MAX_BATCH_BYTES = 500L * 1024 * 1024;
    /** 临时 ZIP 保留时长：超过后启动时清理 */
    private static final long TEMP_ZIP_RETAIN_HOURS = 24;

    private final FileProperties fileProperties;

    public FileDownloadService(FileProperties fileProperties) {
        this.fileProperties = fileProperties;
        // 初始化打包目录
        try {
            Path packageDir = Paths.get(fileProperties.getPackagePath());
            Files.createDirectories(packageDir);
            cleanupExpiredPackages(packageDir);
        } catch (IOException e) {
            log.error("初始化打包目录失败", e);
        }
    }

    /**
     * 解析并校验文件路径：允许相对路径与绝对路径，但真实路径必须落在配置的根目录内。
     *
     * <p>仅 normalize 拦不住 {@code /etc/passwd} 这类绝对路径，必须做真实路径前缀校验；
     * 并用 toRealPath 解析符号链接与挂载点，防止软链逃逸。</p>
     */
    private Path resolveWithinRoots(String filePath) throws IOException {
        if (!StringUtils.hasText(filePath)) {
            throw new IOException("文件路径不能为空");
        }
        Path input;
        try {
            input = Paths.get(filePath);
        } catch (java.nio.file.InvalidPathException e) {
            // 非法字符不应变成 500，统一按「文件不存在/路径非法」处理
            throw new IOException("非法的文件路径");
        }
        Path root = realRoot(fileProperties.getRootPath());
        Path resolved = input.isAbsolute()
                ? input.normalize()
                : root.resolve(input).normalize();
        if (!Files.exists(resolved) || !Files.isRegularFile(resolved)) {
            throw new IOException("文件不存在: " + filePath);
        }
        Path real = resolved.toRealPath();
        for (Path allowed : allowedRoots()) {
            if (real.startsWith(allowed)) {
                return real;
            }
        }
        throw new IOException("非法的文件路径: " + filePath);
    }

    /** 允许读写的根目录清单 */
    private List<Path> allowedRoots() {
        return List.of(
                realRoot(fileProperties.getRootPath()),
                realRoot(fileProperties.getDecompressPath()),
                realRoot(fileProperties.getPackagePath()));
    }

    /**
     * 解析根目录真实路径。部署时 uploads 常是挂载点/软链接，
     * 若只比较未解析的绝对路径，会把合法文件误判为越界。
     */
    private Path realRoot(String configuredPath) {
        Path path = Paths.get(configuredPath).toAbsolutePath().normalize();
        try {
            return Files.exists(path) ? path.toRealPath() : path;
        } catch (IOException e) {
            return path;
        }
    }

    /** 启动时清理超过保留期的临时 ZIP，避免历史版本遗留文件把磁盘占满 */
    private void cleanupExpiredPackages(Path packageDir) {
        Instant deadline = Instant.now().minus(TEMP_ZIP_RETAIN_HOURS, ChronoUnit.HOURS);
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(packageDir, "*.zip")) {
            for (Path path : stream) {
                try {
                    if (Files.getLastModifiedTime(path).toInstant().isBefore(deadline)) {
                        Files.deleteIfExists(path);
                    }
                } catch (IOException e) {
                    log.warn("清理过期打包文件失败: {}", path);
                }
            }
        } catch (IOException e) {
            log.warn("扫描打包目录失败: {}", e.getMessage());
        }
    }

    /**
     * 标准文件下载（Content-Disposition: attachment）
     *
     * @param filePath 文件路径
     * @return ResponseEntity包含文件流
     * @throws IOException 文件不存在或读取失败
     */
    public ResponseEntity<Resource> download(String filePath) throws IOException {
        Path path = resolveWithinRoots(filePath);
        File file = path.toFile();

        String contentType = Files.probeContentType(path);
        if (contentType == null) {
            contentType = "application/octet-stream";
        }

        Resource resource = new FileSystemResource(file);
        String encodedFilename = URLEncoder.encode(file.getName(), StandardCharsets.UTF_8)
                .replace("+", "%20");

        log.info("文件下载: path={}, size={}", filePath, file.length());

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, 
                        "attachment; filename=\"" + encodedFilename + "\"; filename*=UTF-8''" + encodedFilename)
                .contentLength(file.length())
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .body(resource);
    }

    /**
     * 支持断点续传的文件下载（HTTP Range请求）
     * 客户端可通过Range头指定下载范围，实现断点续传
     *
     * @param filePath 文件路径
     * @param rangeHeader HTTP Range请求头（如 "bytes=0-1023"）
     * @return ResponseEntity包含部分文件流
     * @throws IOException 文件不存在或读取失败
     */
    public ResponseEntity<Resource> downloadWithRange(String filePath, String rangeHeader) throws IOException {
        Path path = resolveWithinRoots(filePath);
        File file = path.toFile();

        String contentType = Files.probeContentType(path);
        if (contentType == null) {
            contentType = "application/octet-stream";
        }

        long fileLength = file.length();
        String encodedFilename = URLEncoder.encode(file.getName(), StandardCharsets.UTF_8)
                .replace("+", "%20");

        // 如果没有Range头，返回完整文件
        if (rangeHeader == null || rangeHeader.isEmpty()) {
            return download(filePath);
        }

        // 解析Range头：格式非法按 416 处理，不能把 IllegalArgumentException 抛成 500
        List<HttpRange> ranges;
        try {
            ranges = HttpRange.parseRanges(rangeHeader);
        } catch (IllegalArgumentException e) {
            return rangeNotSatisfiable(fileLength);
        }
        if (ranges.isEmpty()) {
            return download(filePath);
        }

        // 只处理第一个Range（单范围）
        HttpRange range = ranges.get(0);
        long start = range.getRangeStart(fileLength);
        long end = range.getRangeEnd(fileLength);
        // 起点越界或终点小于起点时返回 416，避免返回负长度/空内容
        if (start < 0 || start >= fileLength || end >= fileLength || end < start) {
            return rangeNotSatisfiable(fileLength);
        }
        long contentLength = end - start + 1;

        log.info("断点续传下载: path={}, range={}={}, total={}", filePath, start, end, fileLength);

        // 返回206 Partial Content
        return ResponseEntity.status(206)
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, 
                        "attachment; filename=\"" + encodedFilename + "\"; filename*=UTF-8''" + encodedFilename)
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .header(HttpHeaders.CONTENT_RANGE, 
                        "bytes " + start + "-" + end + "/" + fileLength)
                .contentLength(contentLength)
                .body(new RangeResource(file, start, contentLength));
    }

    /** Range 不可满足时返回 416，并告知实际文件长度 */
    private ResponseEntity<Resource> rangeNotSatisfiable(long fileLength) {
        return ResponseEntity.status(416)
                .header(HttpHeaders.CONTENT_RANGE, "bytes */" + fileLength)
                .build();
    }

    /**
     * 批量文件打包下载（生成ZIP文件）
     * 将多个文件打包成ZIP后返回下载
     *
     * @param filePaths 要打包的文件路径列表
     * @param zipFileName ZIP文件名（不含扩展名）
     * @return ResponseEntity包含ZIP文件流
     * @throws IOException 打包失败
     */
    public ResponseEntity<Resource> batchDownload(List<String> filePaths, String zipFileName) throws IOException {
        if (filePaths == null || filePaths.isEmpty()) {
            throw new IOException("文件列表不能为空");
        }
        if (filePaths.size() > MAX_BATCH_FILES) {
            throw new IOException("一次最多打包 " + MAX_BATCH_FILES + " 个文件，当前 " + filePaths.size() + " 个");
        }

        // 先解析并校验所有路径，累计总大小；任何一个越界都整体拒绝，不生成半成品
        List<Path> resolvedPaths = new java.util.ArrayList<>(filePaths.size());
        long totalBytes = 0L;
        for (String filePath : filePaths) {
            Path path = resolveWithinRoots(filePath);
            long size = Files.size(path);
            totalBytes += size;
            if (totalBytes > MAX_BATCH_BYTES) {
                throw new IOException("批量下载总大小超过上限 " + (MAX_BATCH_BYTES / 1024 / 1024) + "MB");
            }
            resolvedPaths.add(path);
        }

        // 生成临时ZIP文件；文件名去掉路径分隔符，避免 zipFileName 携带 ../ 改变落点
        String requestedName = StringUtils.hasText(zipFileName) ? zipFileName : "download";
        String safeName = requestedName.replaceAll("[\\\\/:*?\"<>|]", "_");
        String actualZipName = safeName + ".zip";
        Path zipPath = Paths.get(fileProperties.getPackagePath(), UUID.randomUUID() + "_" + actualZipName);

        boolean success = false;
        try (ZipOutputStream zos = new ZipOutputStream(
                new BufferedOutputStream(Files.newOutputStream(zipPath)))) {
            int index = 0;
            for (Path path : resolvedPaths) {
                // 同名文件加序号前缀，避免 ZIP 条目重名直接抛 ZipException
                String entryName = (++index) + "_" + path.getFileName();
                zos.putNextEntry(new ZipEntry(entryName));
                try (BufferedInputStream bis = new BufferedInputStream(Files.newInputStream(path))) {
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = bis.read(buffer)) != -1) {
                        zos.write(buffer, 0, bytesRead);
                    }
                }
                zos.closeEntry();
            }
            success = true;
        } finally {
            if (!success) {
                Files.deleteIfExists(zipPath);
            }
        }

        File zipFile = zipPath.toFile();
        // 下载流关闭（或被中断）时删除临时 ZIP，避免每次批量下载都留下永久文件
        Resource resource = new FileSystemResource(zipFile) {
            @Override
            public InputStream getInputStream() throws IOException {
                InputStream delegate = super.getInputStream();
                return new FilterInputStream(delegate) {
                    @Override
                    public void close() throws IOException {
                        try {
                            super.close();
                        } finally {
                            Files.deleteIfExists(zipPath);
                        }
                    }
                };
            }
        };
        String encodedFilename = URLEncoder.encode(actualZipName, StandardCharsets.UTF_8)
                .replace("+", "%20");

        log.info("批量打包下载完成: 文件数={}, 总字节={}, ZIP大小={}", resolvedPaths.size(), totalBytes, zipFile.length());

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + encodedFilename + "\"; filename*=UTF-8''" + encodedFilename)
                .contentLength(zipFile.length())
                .body(resource);
    }

    /**
     * 清理临时打包的ZIP文件
     *
     * @param zipFilePath ZIP文件路径
     */
    public void cleanupPackageFile(String zipFilePath) {
        try {
            Path path = Paths.get(zipFilePath);
            Files.deleteIfExists(path);
            log.debug("临时ZIP文件已清理: {}", zipFilePath);
        } catch (IOException e) {
            log.warn("清理临时ZIP文件失败: {}", zipFilePath);
        }
    }

    /**
     * 自定义Resource实现，支持读取指定范围的文件内容
     * 用于断点续传场景
     */
    private static class RangeResource extends FileSystemResource {
        /** 读取起始字节位置 */
        private final long start;
        /** 本次返回的内容长度（Range片段长度） */
        private final long contentLength;

        public RangeResource(File file, long start, long contentLength) {
            super(file);
            this.start = start;
            this.contentLength = contentLength;
        }

        @Override
        public long contentLength() {
            return contentLength;
        }

        @Override
        public InputStream getInputStream() throws IOException {
            FileInputStream fis = new FileInputStream(getFile());
            try {
                // 跳过前面的字节
                long skipped = fis.skip(start);
                if (skipped != start) {
                    throw new IOException("无法跳过到指定位置: expected=" + start + ", actual=" + skipped);
                }
                // 返回限制长度的InputStream
                return new BoundedInputStream(fis, contentLength);
            } catch (IOException e) {
                // skip 失败或包装失败时同样要关闭句柄，否则下载异常会泄漏文件描述符
                fis.close();
                throw e;
            }
        }
    }

    /**
     * 限制读取长度的InputStream包装器
     */
    private static class BoundedInputStream extends InputStream {
        /** 被包装的实际输入流 */
        private final InputStream delegate;
        /** 剩余可读取字节数（读到0即返回EOF） */
        private long remaining;

        public BoundedInputStream(InputStream delegate, long maxLength) {
            this.delegate = delegate;
            this.remaining = maxLength;
        }

        @Override
        public int read() throws IOException {
            if (remaining <= 0) {
                return -1;
            }
            int result = delegate.read();
            if (result != -1) {
                remaining--;
            }
            return result;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (remaining <= 0) {
                return -1;
            }
            int toRead = (int) Math.min(len, remaining);
            int bytesRead = delegate.read(b, off, toRead);
            if (bytesRead > 0) {
                remaining -= bytesRead;
            }
            return bytesRead;
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }
    }
}
