package com.ai.cs.knowledge.util;

import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 上传临时文件与按 ID 找已存文件。文件名只保留安全字符，避免路径穿越和前缀误匹配。
 */
public final class UploadFiles {

    private UploadFiles() {
    }

    /** 业务文件 ID：字母数字、下划线、短横线，长度 1~64 */
    public static boolean safeId(String fileId) {
        return fileId != null && fileId.matches("[A-Za-z0-9_-]{1,64}");
    }

    /**
     * 在目录中查找「文件名等于 ID」或「ID.扩展名」的文件。
     * 不用 startsWith(id)，避免 id=1 命中 10.jpg。
     */
    public static File findById(File dir, String fileId) {
        File[] files = listById(dir, fileId);
        if (files.length == 0) {
            return null;
        }
        Arrays.sort(files, Comparator.comparing(File::getName));
        return files[0];
    }

    /** 派生文件：ID + marker，或 ID + marker + 扩展名。marker 例如 _thumb、_waveform。 */
    public static File findDerived(File dir, String fileId, String marker) {
        if (!safeId(fileId) || dir == null || !dir.isDirectory() || marker == null || marker.isEmpty()) {
            return null;
        }
        File[] files = dir.listFiles((d, name) -> matchesDerived(name, fileId, marker));
        if (files == null || files.length == 0) {
            return null;
        }
        Arrays.sort(files, Comparator.comparing(File::getName));
        return files[0];
    }

    /** 文件名等于 ID，或为「ID.扩展名」。 */
    public static boolean matchesId(String name, String fileId) {
        if (!safeId(fileId) || name == null) {
            return false;
        }
        return name.equals(fileId) || name.startsWith(fileId + ".");
    }

    /** 派生文件：ID + marker，或 ID + marker + 扩展名。 */
    public static boolean matchesDerived(String name, String fileId, String marker) {
        if (!safeId(fileId) || name == null || marker == null || marker.isEmpty()) {
            return false;
        }
        String base = fileId + marker;
        return name.equals(base) || name.startsWith(base + ".");
    }

    public static File[] listById(File dir, String fileId) {
        if (!safeId(fileId) || dir == null || !dir.isDirectory()) {
            return new File[0];
        }
        File[] files = dir.listFiles((d, name) -> matchesId(name, fileId));
        return files == null ? new File[0] : files;
    }

    /**
     * 把上传内容写到系统临时目录下的 ai-cs-upload，文件名去掉路径和特殊字符。
     */
    public static File saveTemp(MultipartFile file) throws IOException {
        String base = sanitizeName(file == null ? null : file.getOriginalFilename());
        Path dir = Paths.get(System.getProperty("java.io.tmpdir"), "ai-cs-upload").toAbsolutePath().normalize();
        Files.createDirectories(dir);
        String stored = System.currentTimeMillis() + "_" + ThreadLocalRandom.current().nextInt(1000, 10000) + "_" + base;
        Path dest = dir.resolve(stored).normalize();
        if (!dest.startsWith(dir)) {
            throw new IOException("非法文件名");
        }
        if (file == null) {
            throw new IOException("上传文件为空");
        }
        file.transferTo(dest);
        return dest.toFile();
    }

    private static String sanitizeName(String original) {
        String name = original == null ? "file" : original.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        name = name.replaceAll("[^A-Za-z0-9._-]", "_");
        if (name.isBlank() || ".".equals(name) || "..".equals(name)) {
            name = "file";
        }
        if (name.length() > 80) {
            name = name.substring(name.length() - 80);
        }
        return name;
    }
}
