package com.ai.cs.common.util;

import com.ai.cs.common.exception.BusinessException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Map;

/**
 * 上传文件魔数校验：不以 Content-Type / 扩展名为准，防止伪造类型绕过白名单。
 */
public final class FileMagicValidator {

    /** 常见文件头 → 扩展名（十六进制大写，按前缀匹配） */
    private static final Map<String, String> MAGIC = Map.ofEntries(
            Map.entry("FFD8FF", "jpg"),
            Map.entry("89504E47", "png"),
            Map.entry("47494638", "gif"),
            Map.entry("52494646", "webp"),
            Map.entry("424D", "bmp"),
            Map.entry("25504446", "pdf"),
            Map.entry("504B0304", "zip"),
            Map.entry("494433", "mp3"),
            Map.entry("FFF3", "mp3"),
            Map.entry("FFF2", "mp3"),
            Map.entry("FFFB", "mp3"),
            Map.entry("664C6143", "flac"),
            Map.entry("4F676753", "ogg")
    );

    private FileMagicValidator() {
    }

    /**
     * 校验上传文件的魔数与扩展名一致。
     *
     * @param file          上传文件
     * @param allowedExts   允许的扩展名（小写，不含点），如 jpg/png
     */
    public static void assertAllowed(MultipartFile file, String... allowedExts) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("文件不能为空");
        }
        String original = file.getOriginalFilename();
        if (original == null || !original.contains(".")) {
            throw new BusinessException("文件名非法");
        }
        String ext = original.substring(original.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        boolean extAllowed = false;
        for (String allowed : allowedExts) {
            if (allowed.equalsIgnoreCase(ext) || ("jpeg".equals(ext) && "jpg".equalsIgnoreCase(allowed))) {
                extAllowed = true;
                break;
            }
        }
        if (!extAllowed) {
            throw new BusinessException("不支持的文件类型: " + ext);
        }
        String hex = readHeaderHex(file);
        String detected = detectExt(hex);
        if (detected == null) {
            throw new BusinessException("无法识别文件类型，可能已被篡改");
        }
        if (!compatible(ext, detected)) {
            throw new BusinessException("文件内容与扩展名不匹配");
        }
    }

    /** 读取文件头 8 字节并转大写十六进制 */
    private static String readHeaderHex(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            byte[] header = new byte[8];
            int n = in.read(header);
            if (n < 2) {
                throw new BusinessException("文件太小，无法识别类型");
            }
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < n; i++) {
                sb.append(String.format("%02X", header[i] & 0xFF));
            }
            return sb.toString();
        } catch (IOException e) {
            throw new BusinessException("读取文件失败");
        }
    }

    /** 按最长前缀匹配魔数 */
    private static String detectExt(String hex) {
        String hit = null;
        int best = 0;
        for (Map.Entry<String, String> e : MAGIC.entrySet()) {
            if (hex.startsWith(e.getKey()) && e.getKey().length() > best) {
                hit = e.getValue();
                best = e.getKey().length();
            }
        }
        // RIFF....WEBP：webp 的完整特征在第 8-12 字节，这里用 RIFF 先识别为 webp 候选
        if ("webp".equals(hit) && hex.length() >= 16 && !hex.substring(8).startsWith("57454250") && hex.startsWith("52494646")) {
            // 可能是 wav（RIFF WAVE），按音频处理时由调用方扩展名再约束
            return hit;
        }
        return hit;
    }

    /** jpg/jpeg、zip/docx 等兼容别名 */
    private static boolean compatible(String ext, String detected) {
        if (ext.equals(detected)) {
            return true;
        }
        if ("jpeg".equals(ext) && "jpg".equals(detected)) {
            return true;
        }
        if ("jpg".equals(ext) && "jpeg".equals(detected)) {
            return true;
        }
        // docx/xlsx 实质是 zip
        if ("zip".equals(detected) && (ext.equals("docx") || ext.equals("xlsx") || ext.equals("pptx"))) {
            return true;
        }
        // RIFF 容器：webp / wav 文件头相同，由调用方扩展名再约束
        if ("webp".equals(detected) && ("webp".equals(ext) || "wav".equals(ext))) {
            return true;
        }
        return false;
    }
}
