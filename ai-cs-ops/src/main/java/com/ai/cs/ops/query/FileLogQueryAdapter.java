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

@Component
public class FileLogQueryAdapter implements LogQueryAdapter {

    private static final Pattern PLAIN = Pattern.compile(
            "^(?<ts>\\d{4}-\\d{2}-\\d{2}[ T]\\d{2}:\\d{2}:\\d{2}(?:\\.\\d{1,3})?)\\s+(?<level>TRACE|DEBUG|INFO|WARN|ERROR|FATAL)\\b(?<rest>.*)$");
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
        int from = Math.min((page - 1) * size, all.size());
        int to = Math.min(from + size, all.size());
        pageVO.setList(all.subList(from, to));
        return pageVO;
    }

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

    @Override
    public int fileCount() {
        return resolveFiles().size();
    }

    @Override
    public int recentErrorCount() {
        return (int) loadAll().stream()
                .filter(item -> "ERROR".equalsIgnoreCase(item.getLevel()) || "WARN".equalsIgnoreCase(item.getLevel()))
                .count();
    }

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

    private List<Path> resolveFiles() {
        Set<Path> files = new LinkedHashSet<>();
        Path cwd = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        List<Path> dirs = new ArrayList<>();
        for (String dir : opsProperties.getLogDirs()) {
            Path configured = Path.of(dir);
            dirs.add(configured.isAbsolute() ? configured : cwd.resolve(configured));
        }
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

    private static List<String> tailLines(Path file, int limit) throws IOException {
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        if (lines.size() <= limit) {
            return lines;
        }
        return lines.subList(lines.size() - limit, lines.size());
    }

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

    private static String extractQuoted(String text, String... keys) {
        for (String key : keys) {
            Matcher matcher = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"([^\"]*)\"").matcher(text);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        return "";
    }

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

    private static String guessService(Path file) {
        String name = file.getFileName().toString();
        int dot = name.indexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static String firstText(JSONObject json, String... keys) {
        for (String key : keys) {
            String value = json.getString(key);
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return "";
    }

    private static boolean contains(String value, String expect) {
        if (!StringUtils.hasText(expect)) {
            return true;
        }
        return blank(value).toLowerCase(Locale.ROOT).contains(expect.trim().toLowerCase(Locale.ROOT));
    }

    private static String blank(String value) {
        return value == null ? "" : value;
    }
}
