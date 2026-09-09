package com.ai.cs.ops.query;

import com.ai.cs.ops.config.OpsProperties;
import com.ai.cs.ops.dto.OpsLogEntryVO;
import com.ai.cs.ops.dto.OpsLogPageVO;
import com.ai.cs.ops.dto.OpsLogQuery;
import com.ai.cs.ops.service.LogRedactor;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 本地滚动日志文件查询适配器（单机/POC 实现）。
 * 扫描工作目录及配置目录下的 *.log 文件，兼容「普通文本行」与「JSON 行」两种格式，
 * 支持多条件过滤与分页；所有 message 在入库到视图前都会经 LogRedactor 脱敏。
 * 后续接入 Loki/ES 时只需新增适配器实现，上层接口形状不变。
 */
@Component
public class FileLogQueryAdapter implements LogQueryAdapter {

    /** 普通文本日志行格式：时间戳 + 级别 + 正文（如 2026-09-07 12:00:00.123 INFO ...） */
    private static final Pattern PLAIN = Pattern.compile(
            "^(?<ts>\\d{4}-\\d{2}-\\d{2}[ T]\\d{2}:\\d{2}:\\d{2}(?:\\.\\d{1,3})?)\\s+(?<level>TRACE|DEBUG|INFO|WARN|ERROR|FATAL)\\b(?<rest>.*)$");
    /** 从日志正文中提取 requestId/traceId/sessionId/tenantId 等键值对 */
    private static final Pattern KV = Pattern.compile("(?i)\\b(requestId|traceId|sessionId|tenantId)\\s*[=:]\\s*([A-Za-z0-9._\\-]+)");

    @Resource
    private OpsProperties opsProperties;
    @Resource
    private LogRedactor logRedactor;

    @Override
    public String source() {
        return "file";
    }

    @Override
    public String hint() {
        return "当前为本地滚动日志适配器（单机/POC）。接上 Loki/ES 后只换适配器，接口形状不变。";
    }

    /**
     * 多条件分页搜索日志：全量加载 → 条件过滤 → 按时间倒序 → 内存分页。
     * 页码最小 1，页大小限制在 1~200，防止一次拉取过多拖垮内存。
     */
    @Override
    public OpsLogPageVO search(OpsLogQuery query) {
        int page = query.getPage() == null || query.getPage() < 1 ? 1 : query.getPage();
        int size = query.getSize() == null ? 50 : Math.min(Math.max(query.getSize(), 1), 200);
        List<OpsLogEntryVO> all = loadAll().stream()
                .filter(item -> match(item, query))
                .sorted(Comparator.comparing(OpsLogEntryVO::getTimestamp, Comparator.nullsLast(String::compareTo)).reversed())
                .collect(Collectors.toList());
        OpsLogPageVO pageVO = new OpsLogPageVO();
        pageVO.setSource(source());
        pageVO.setHint(hint());
        pageVO.setTotal(all.size());
        pageVO.setPage(page);
        pageVO.setSize(size);
        // 内存分页切片，from/to 均做越界保护
        int from = Math.min((page - 1) * size, all.size());
        int to = Math.min(from + size, all.size());
        pageVO.setList(all.subList(from, to));
        return pageVO;
    }

    /**
     * 按请求 ID 查询全链路日志，按时间正序返回，便于还原一次请求的完整处理过程。
     */
    @Override
    public List<OpsLogEntryVO> byRequestId(String requestId) {
        if (!StringUtils.hasText(requestId)) {
            return List.of();
        }
        OpsLogQuery query = new OpsLogQuery();
        query.setRequestId(requestId.trim());
        return loadAll().stream()
                .filter(item -> match(item, query))
                .sorted(Comparator.comparing(OpsLogEntryVO::getTimestamp, Comparator.nullsLast(String::compareTo)))
                .collect(Collectors.toList());
    }

    /**
     * 扫描到的日志文件数。
     */
    @Override
    public int fileCount() {
        return resolveFiles().size();
    }

    /**
     * 近期 WARN/ERROR 级别日志条数，供总览大盘展示。
     */
    @Override
    public int recentErrorCount() {
        return (int) loadAll().stream()
                .filter(item -> "ERROR".equalsIgnoreCase(item.getLevel()) || "WARN".equalsIgnoreCase(item.getLevel()))
                .count();
    }

    /**
     * 单条日志是否命中查询条件：ID 类字段模糊包含、级别精确匹配、
     * 关键词匹配内容或 Logger 名、时间范围按字符串比较（日志时间格式统一时可比）。
     */
    private boolean match(OpsLogEntryVO item, OpsLogQuery query) {
        if (!contains(item.getRequestId(), query.getRequestId())) {
            return false;
        }
        if (!contains(item.getTraceId(), query.getTraceId())) {
            return false;
        }
        if (!contains(item.getSessionId(), query.getSessionId())) {
            return false;
        }
        if (!contains(item.getTenantId(), query.getTenantId())) {
            return false;
        }
        if (StringUtils.hasText(query.getService()) && !contains(item.getService(), query.getService())) {
            return false;
        }
        if (StringUtils.hasText(query.getLevel()) && !query.getLevel().equalsIgnoreCase(blank(item.getLevel()))) {
            return false;
        }
        if (StringUtils.hasText(query.getKeyword()) && !contains(item.getMessage(), query.getKeyword())
                && !contains(item.getLogger(), query.getKeyword())) {
            return false;
        }
        if (StringUtils.hasText(query.getFrom()) && blank(item.getTimestamp()).compareTo(query.getFrom()) < 0) {
            return false;
        }
        if (StringUtils.hasText(query.getTo()) && blank(item.getTimestamp()).compareTo(query.getTo()) > 0) {
            return false;
        }
        return true;
    }

    /**
     * 加载全部日志文件的尾部若干行并逐行解析；单文件读取失败时跳过，不影响其他文件。
     */
    private List<OpsLogEntryVO> loadAll() {
        List<OpsLogEntryVO> rows = new ArrayList<>();
        int limit = Math.max(opsProperties.getMaxLinesPerFile(), 100);
        for (Path file : resolveFiles()) {
            try {
                List<String> lines = tailLines(file, limit);
                for (String line : lines) {
                    OpsLogEntryVO entry = parse(line, file);
                    if (entry != null) {
                        rows.add(entry);
                    }
                }
            } catch (IOException ignored) {
                // skip unreadable files
            }
        }
        return rows;
    }

    /**
     * 汇总待扫描的日志文件清单：配置目录（相对路径基于工作目录解析）
     * + 工作目录/logs + 上级目录/logs，按文件名含 .log 过滤，去重保序。
     */
    private List<Path> resolveFiles() {
        Set<Path> files = new LinkedHashSet<>();
        Path cwd = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        List<Path> dirs = new ArrayList<>();
        for (String dir : opsProperties.getLogDirs()) {
            Path configured = Path.of(dir);
            dirs.add(configured.isAbsolute() ? configured : cwd.resolve(configured));
        }
        // 兜底再扫工作目录及其父目录下的 logs，覆盖从项目根/模块目录两种启动方式
        dirs.add(cwd.resolve("logs"));
        if (cwd.getParent() != null) {
            dirs.add(cwd.getParent().resolve("logs"));
        }
        for (Path dir : dirs) {
            if (!Files.isDirectory(dir)) {
                continue;
            }
            try (Stream<Path> stream = Files.list(dir)) {
                stream.filter(path -> {
                    String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
                    return Files.isRegularFile(path) && name.contains(".log");
                }).forEach(files::add);
            } catch (IOException ignored) {
                // skip
            }
        }
        return new ArrayList<>(files);
    }

    /**
     * 读取文件尾部 limit 行：全量读入后截尾（POC 实现，大文件由 maxLinesPerFile 控制上限）。
     */
    private static List<String> tailLines(Path file, int limit) throws IOException {
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        if (lines.size() <= limit) {
            return lines;
        }
        return lines.subList(lines.size() - limit, lines.size());
    }

    /**
     * 解析单行日志：
     * 1) 以 { 开头的按 JSON 行解析，JSON 损坏时降级为正则提取关键字段；
     * 2) 普通文本行按「时间戳+级别+正文」格式匹配；
     * 3) 都不匹配时整行作为 message。
     * 无论哪种格式，message 都会脱敏，服务名缺失时按文件名推断。
     */
    private OpsLogEntryVO parse(String line, Path file) {
        if (!StringUtils.hasText(line)) {
            return null;
        }
        OpsLogEntryVO entry = new OpsLogEntryVO();
        entry.setFile(file.getFileName().toString());
        String trimmed = line.trim();
        if (trimmed.startsWith("{")) {
            try {
                JSONObject json = JSON.parseObject(trimmed);
                fillFromJson(entry, json, file);
                return entry;
            } catch (Exception ignored) {
                // JSON 不完整（如写文件中途被读）：降级为正则提取，提到关键字段就算有效行
                fillFromBrokenJson(entry, trimmed, file);
                if (StringUtils.hasText(entry.getTimestamp()) || StringUtils.hasText(entry.getLevel())) {
                    return entry;
                }
            }
        }
        Matcher matcher = PLAIN.matcher(trimmed);
        if (matcher.matches()) {
            entry.setTimestamp(matcher.group("ts"));
            entry.setLevel(matcher.group("level"));
            entry.setMessage(logRedactor.redact(matcher.group("rest")));
        } else {
            entry.setMessage(logRedactor.redact(trimmed));
        }
        fillFromText(entry, trimmed);
        if (!StringUtils.hasText(entry.getService())) {
            entry.setService(guessService(file));
        }
        return entry;
    }

    /**
     * 从 JSON 日志行填充字段：同一含义兼容多种键名（如 ts/timestamp/time）。
     */
    private void fillFromJson(OpsLogEntryVO entry, JSONObject json, Path file) {
        entry.setTimestamp(firstText(json, "ts", "timestamp", "time"));
        entry.setLevel(firstText(json, "level"));
        entry.setService(firstText(json, "service", "app"));
        entry.setLogger(firstText(json, "logger", "loggerName"));
        entry.setMessage(logRedactor.redact(firstText(json, "msg", "message")));
        entry.setRequestId(firstText(json, "requestId"));
        entry.setTraceId(firstText(json, "traceId"));
        entry.setSessionId(firstText(json, "sessionId"));
        entry.setTenantId(firstText(json, "tenantId"));
        fillFromText(entry, entry.getMessage());
        if (!StringUtils.hasText(entry.getService())) {
            entry.setService(guessService(file));
        }
    }

    /**
     * JSON 解析失败时的降级填充：用正则从残破文本中提取 "key":"value" 形式的字段。
     */
    private void fillFromBrokenJson(OpsLogEntryVO entry, String text, Path file) {
        entry.setTimestamp(extractQuoted(text, "ts", "timestamp", "time"));
        entry.setLevel(extractQuoted(text, "level"));
        entry.setService(extractQuoted(text, "service", "app"));
        entry.setLogger(extractQuoted(text, "logger", "loggerName"));
        entry.setMessage(logRedactor.redact(extractQuoted(text, "msg", "message")));
        entry.setRequestId(extractQuoted(text, "requestId"));
        entry.setTraceId(extractQuoted(text, "traceId"));
        entry.setSessionId(extractQuoted(text, "sessionId"));
        entry.setTenantId(extractQuoted(text, "tenantId"));
        fillFromText(entry, text);
        if (!StringUtils.hasText(entry.getService())) {
            entry.setService(guessService(file));
        }
    }

    /**
     * 从文本中提取首个命中的 "key":"value" 字符串值。
     */
    private static String extractQuoted(String text, String... keys) {
        for (String key : keys) {
            Matcher matcher = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"([^\"]*)\"").matcher(text);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        return "";
    }

    /**
     * 从日志正文提取 requestId/traceId/sessionId/tenantId 键值对补充到条目上；
     * 已存在的字段不覆盖（JSON 字段优先于正文提取）。
     */
    private static void fillFromText(OpsLogEntryVO entry, String text) {
        if (!StringUtils.hasText(text)) {
            return;
        }
        Matcher matcher = KV.matcher(text);
        while (matcher.find()) {
            String key = matcher.group(1).toLowerCase(Locale.ROOT);
            String value = matcher.group(2);
            if ("requestid".equals(key) && !StringUtils.hasText(entry.getRequestId())) {
                entry.setRequestId(value);
            } else if ("traceid".equals(key) && !StringUtils.hasText(entry.getTraceId())) {
                entry.setTraceId(value);
            } else if ("sessionid".equals(key) && !StringUtils.hasText(entry.getSessionId())) {
                entry.setSessionId(value);
            } else if ("tenantid".equals(key) && !StringUtils.hasText(entry.getTenantId())) {
                entry.setTenantId(value);
            }
        }
    }

    /**
     * 按日志文件名推断服务名（取第一个 . 之前部分，如 ai-cs-agent.log → ai-cs-agent）。
     */
    private static String guessService(Path file) {
        String name = file.getFileName().toString();
        int dot = name.indexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    /**
     * 从 JSON 中按键名优先级取首个非空字符串值。
     */
    private static String firstText(JSONObject json, String... keys) {
        for (String key : keys) {
            String value = json.getString(key);
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return "";
    }

    /**
     * 模糊包含判断（不区分大小写）；条件为空时视为命中（即该条件不生效）。
     */
    private static boolean contains(String value, String expect) {
        if (!StringUtils.hasText(expect)) {
            return true;
        }
        return blank(value).toLowerCase(Locale.ROOT).contains(expect.trim().toLowerCase(Locale.ROOT));
    }

    /**
     * null 转空串，避免后续比较出现 NPE。
     */
    private static String blank(String value) {
        return value == null ? "" : value;
    }
}
