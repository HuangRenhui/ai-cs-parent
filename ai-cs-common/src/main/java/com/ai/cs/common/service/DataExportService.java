package com.ai.cs.common.service;

import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 数据导出服务
 * 支持 CSV 格式导出
 *
 * @author huangrenhui
 * @date 2026/7/20
 */
@Slf4j
public class DataExportService {

    /**
     * 导出为 CSV 文件
     *
     * @param headers  表头列表
     * @param dataList 数据行列表（每行是一个 Map<String, Object>）
     * @param filePath 导出文件路径
     * @return 导出文件路径
     */
    public String exportToCsv(List<String> headers, List<Map<String, Object>> dataList, String filePath) {
        File file = new File(filePath);
        File parentDir = file.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }

        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {

            // 写入 BOM 头（Excel 兼容 UTF-8）
            writer.write('\ufeff');

            // 写入表头
            writer.write(headers.stream()
                    .map(this::escapeCsv)
                    .collect(Collectors.joining(",")));
            writer.newLine();

            // 写入数据行
            for (Map<String, Object> row : dataList) {
                String line = headers.stream()
                        .map(h -> escapeCsv(String.valueOf(row.getOrDefault(h, ""))))
                        .collect(Collectors.joining(","));
                writer.write(line);
                writer.newLine();
            }

            log.info("CSV导出成功: {}, 共{}行", filePath, dataList.size());
            return filePath;

        } catch (IOException e) {
            log.error("CSV导出失败: {}", e.getMessage());
            throw new RuntimeException("数据导出失败: " + e.getMessage());
        }
    }

    /**
     * 导出为临时文件
     */
    public String exportToTempCsv(List<String> headers, List<Map<String, Object>> dataList) {
        String tempDir = System.getProperty("java.io.tmpdir");
        String fileName = "export_" + System.currentTimeMillis() + ".csv";
        return exportToCsv(headers, dataList, tempDir + File.separator + fileName);
    }

    /**
     * CSV 字段转义（处理包含逗号、引号、换行的情况）
     * 规则：含特殊字符时整体加双引号，内部双引号双写（RFC 4180）
     */
    private String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
