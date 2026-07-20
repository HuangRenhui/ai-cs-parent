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

import java.io.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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

    private final FileProperties fileProperties;

    public FileDownloadService(FileProperties fileProperties) {
        this.fileProperties = fileProperties;
        // 初始化打包目录
        try {
            Files.createDirectories(Paths.get(fileProperties.getPackagePath()));
        } catch (IOException e) {
            log.error("初始化打包目录失败", e);
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
        Path path = Paths.get(filePath).toAbsolutePath().normalize();
        File file = path.toFile();

        if (!file.exists() || !file.isFile()) {
            throw new IOException("文件不存在: " + filePath);
        }

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
        Path path = Paths.get(filePath).toAbsolutePath().normalize();
        File file = path.toFile();

        if (!file.exists() || !file.isFile()) {
            throw new IOException("文件不存在: " + filePath);
        }

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

        // 解析Range头
        List<HttpRange> ranges = HttpRange.parseRanges(rangeHeader);
        if (ranges.isEmpty()) {
            return download(filePath);
        }

        // 只处理第一个Range（单范围）
        HttpRange range = ranges.get(0);
        long start = range.getRangeStart(fileLength);
        long end = range.getRangeEnd(fileLength);
        long contentLength = end - start + 1;

        log.info("断点续传下载: path={}, range={}={}, total={}", filePath, start, end, fileLength);

        // 创建指定范围的Resource
        Resource resource = new FileSystemResource(file);
        try {
            resource = resource.createRelative("");
        } catch (Exception e) {
            // fallback: 使用原始resource
        }

        // 返回206 Partial Content
        return ResponseEntity.status(206)
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, 
                        "attachment; filename=\"" + encodedFilename + "\"; filename*=UTF-8''" + encodedFilename)
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .header(HttpHeaders.CONTENT_RANGE, 
                        "bytes " + start + "-" + end + "/" + fileLength)
                .contentLength(contentLength)
                .body(new RangeResource(resource, start, contentLength));
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

        // 生成临时ZIP文件
        String actualZipName = (zipFileName != null && !zipFileName.isEmpty() ? zipFileName : "download") + ".zip";
        Path zipPath = Paths.get(fileProperties.getPackagePath(), 
                UUID.randomUUID() + "_" + actualZipName);

        try (ZipOutputStream zos = new ZipOutputStream(
                new BufferedOutputStream(Files.newOutputStream(zipPath)))) {

            for (String filePath : filePaths) {
                Path path = Paths.get(filePath).toAbsolutePath().normalize();
                File file = path.toFile();

                if (!file.exists() || !file.isFile()) {
                    log.warn("跳过不存在的文件: {}", filePath);
                    continue;
                }

                // 使用文件名作为ZIP条目名（避免路径问题）
                String entryName = file.getName();
                zos.putNextEntry(new ZipEntry(entryName));

                // 写入文件内容
                try (BufferedInputStream bis = new BufferedInputStream(
                        new FileInputStream(file))) {
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = bis.read(buffer)) != -1) {
                        zos.write(buffer, 0, bytesRead);
                    }
                }

                zos.closeEntry();
                log.debug("已添加到ZIP: {}", entryName);
            }
        }

        File zipFile = zipPath.toFile();
        Resource resource = new FileSystemResource(zipFile);
        String encodedFilename = URLEncoder.encode(actualZipName, StandardCharsets.UTF_8)
                .replace("+", "%20");

        log.info("批量打包下载完成: 文件数={}, ZIP大小={}", filePaths.size(), zipFile.length());

        // 返回ZIP文件（注意：临时ZIP文件需要在下载完成后清理）
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
        private final long start;
        private final long contentLength;

        public RangeResource(Resource delegate, long start, long contentLength) {
            super(((FileSystemResource) delegate).getFile());
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
            // 跳过前面的字节
            long skipped = fis.skip(start);
            if (skipped != start) {
                fis.close();
                throw new IOException("无法跳过到指定位置: expected=" + start + ", actual=" + skipped);
            }
            // 返回限制长度的InputStream
            return new BoundedInputStream(fis, contentLength);
        }
    }

    /**
     * 限制读取长度的InputStream包装器
     */
    private static class BoundedInputStream extends InputStream {
        private final InputStream delegate;
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
