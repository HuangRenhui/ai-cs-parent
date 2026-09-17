package com.ai.cs.knowledge.controller;

import com.ai.cs.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 提示词版本与发布控制器（占位实现）。
 *
 * <p>解决「改提示词不敢直接上线」：改动先落草稿，评测通过后按比例灰度，出问题一键回滚。</p>
 *
 * <p><b>当前状态</b>：内存桩，数据存在本地 List 里，<b>服务重启即丢</b>，仅用于跑通前后端契约。
 * 表结构与完整接口约定见 {@code docs/提示词版本与评测-接口约定.md}。</p>
 *
 * <p><b>TODO 后续实现</b>：
 * <ol>
 *   <li>建表 {@code cs_prompt_version}，{@code config_json} 用 JSON 列存配置快照</li>
 *   <li>同一 target 下最多一个 published：全量发布时旧的自动降级为 draft</li>
 *   <li>published / gray 状态禁止删除，否则线上无处可回退</li>
 *   <li>灰度按 tenantCode + sessionId 做一致性哈希，保证同一会话稳定命中同一版本</li>
 *   <li>creator 从登录态取，不信任前端传值；发布与回滚必须写操作审计</li>
 * </ol>
 *
 * @author huangrenhui
 */
@Slf4j
@RestController
@RequestMapping("/api/prompt/version")
public class PromptVersionController {

    /** 内存版本表，重启即丢 */
    private final List<Map<String, Object>> versions = new ArrayList<>();
    private final AtomicLong idSeq = new AtomicLong(7110);

    @PostConstruct
    void seed() {
        versions.add(build(7101L, "v1.4.0", "电商售后专用", "draft", 0,
                "新增「违规问题拒答话术」，开启提示词注入防护", "超级管理员", "2026-09-15 10:20:00"));
        versions.add(build(7102L, "v1.3.0", "电商售后专用", "gray", 20,
                "上下文条数 3→5，观察召回变多后是否更容易答偏", "运营管理员", "2026-09-12 16:05:00"));
        versions.add(build(7103L, "v1.2.0", "电商售后专用", "published", 100,
                "严格按格式输出，关闭思维链降低首字延迟", "运营管理员", "2026-09-05 09:30:00"));
        versions.add(build(7104L, "v1.1.0", "电商售后专用", "rolled_back", 0,
                "引入少样本示例（答话变长且偏离口径，已回滚）", "运营管理员", "2026-08-28 14:10:00"));
        versions.add(build(7105L, "v1.0.0", "平台默认", "published", 100,
                "初始版本", "超级管理员", "2026-08-01 09:00:00"));
    }

    private Map<String, Object> build(Long id, String version, String target, String status,
                                      int grayScale, String changeNote, String creator, String createTime) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("version", version);
        m.put("target", target);
        m.put("status", status);
        m.put("grayScale", grayScale);
        m.put("changeNote", changeNote);
        m.put("creator", creator);
        m.put("createTime", createTime);
        m.put("config", new LinkedHashMap<String, Object>());
        return m;
    }

    private Map<String, Object> find(Long id) {
        return versions.stream()
                .filter(v -> Objects.equals(String.valueOf(v.get("id")), String.valueOf(id)))
                .findFirst().orElse(null);
    }

    /**
     * 版本列表，按创建时间倒序。
     *
     * <p>列表必须带 config：前端「版本对比」直接在这份数据上做字段 diff，不再单独拉详情。</p>
     */
    @GetMapping("/list")
    @Operation(summary = "版本列表", description = "当前为占位实现，返回内存演示数据，重启即丢")
    public Result<List<Map<String, Object>>> list(
            @Parameter(description = "适用对象，可选") @RequestParam(required = false) String target) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> v : versions) {
            if (target == null || target.isBlank() || target.equals(v.get("target"))) {
                rows.add(v);
            }
        }
        return Result.success(rows);
    }

    /** 单个版本详情 */
    @GetMapping("/{id}")
    @Operation(summary = "版本详情", description = "当前为占位实现")
    public Result<Map<String, Object>> detail(@Parameter(description = "版本ID") @PathVariable Long id) {
        Map<String, Object> row = find(id);
        return row == null ? Result.fail("版本不存在") : Result.success(row);
    }

    /**
     * 保存为新版本（草稿）。
     *
     * <p>TODO 后续实现：changeNote 必填校验；creator 从登录态取；一律存为 draft。</p>
     */
    @PostMapping("/save")
    @Operation(summary = "保存为新版本", description = "保存为草稿，当前为占位实现，不会持久化")
    public Result<String> save(@RequestBody Map<String, Object> body) {
        Object note = body.get("changeNote");
        if (note == null || String.valueOf(note).isBlank()) {
            return Result.fail("请填写变更说明，方便日后回溯");
        }
        log.info("[占位] 收到版本保存请求（未持久化）: version={}, target={}",
                body.get("version"), body.get("target"));

        Map<String, Object> row = new LinkedHashMap<>(body);
        row.put("id", idSeq.incrementAndGet());
        row.putIfAbsent("version", "v1." + versions.size() + ".0");
        row.putIfAbsent("target", "平台默认");
        row.put("status", "draft");
        row.put("grayScale", 0);
        row.put("creator", "当前登录人");
        row.put("createTime", "2026-09-15 00:00:00");
        versions.add(0, row);

        return Result.success("已存为草稿，评测通过后再发布（后端暂未实现持久化）");
    }

    /**
     * 发布或调整放量。
     *
     * <p>TODO 后续实现：grayScale=100 时同 target 旧的 published 自动降级为 draft；
     * grayScale&lt;100 时置为 gray 且不动现有全量版本；灰度需按会话做一致性哈希分流。</p>
     *
     * @param grayScale 生效流量百分比，100 表示全量
     */
    @PutMapping("/{id}/publish")
    @Operation(summary = "发布版本", description = "grayScale=100 全量发布，0~99 灰度；当前为占位实现")
    public Result<String> publish(
            @Parameter(description = "版本ID") @PathVariable Long id,
            @Parameter(description = "生效流量百分比") @RequestParam(defaultValue = "100") Integer grayScale) {
        Map<String, Object> row = find(id);
        if (row == null) {
            return Result.fail("版本不存在");
        }
        int gray = grayScale == null ? 100 : grayScale;
        if (gray >= 100) {
            for (Map<String, Object> v : versions) {
                if (Objects.equals(v.get("target"), row.get("target"))
                        && "published".equals(v.get("status"))) {
                    v.put("status", "draft");
                }
            }
        }
        row.put("status", gray >= 100 ? "published" : "gray");
        row.put("grayScale", gray);
        log.info("[占位] 发布版本（仅改内存）: id={}, grayScale={}", id, gray);
        return Result.success(gray >= 100 ? "已全量发布（后端暂未真正生效）" : "已发布，" + gray + "% 流量先跑（后端暂未真正生效）");
    }

    /**
     * 回滚到指定版本。
     *
     * <p>TODO 后续实现：把该版本快照写回当前生效配置；同 target 其它在线版本降级；写操作审计。</p>
     */
    @PutMapping("/{id}/rollback")
    @Operation(summary = "回滚版本", description = "当前为占位实现，不会真正改写生效配置")
    public Result<String> rollback(@Parameter(description = "版本ID") @PathVariable Long id) {
        Map<String, Object> row = find(id);
        if (row == null) {
            return Result.fail("版本不存在");
        }
        for (Map<String, Object> v : versions) {
            if (Objects.equals(v.get("target"), row.get("target")) && !v.equals(row)
                    && ("published".equals(v.get("status")) || "gray".equals(v.get("status")))) {
                v.put("status", "draft");
            }
        }
        row.put("status", "published");
        row.put("grayScale", 100);
        log.info("[占位] 回滚版本（仅改内存）: id={}", id);
        return Result.success("已回滚到 " + row.get("version") + "（后端暂未真正改写生效配置）");
    }

    /**
     * 删除非生效版本。
     *
     * <p>TODO 后续实现：后端仍需兜底校验，published / gray 一律拒绝删除。</p>
     */
    @DeleteMapping("/delete/{id}")
    @Operation(summary = "删除版本", description = "生效中的版本不允许删除；当前为占位实现")
    public Result<String> delete(@Parameter(description = "版本ID") @PathVariable Long id) {
        Map<String, Object> row = find(id);
        if (row == null) {
            return Result.fail("版本不存在");
        }
        String status = String.valueOf(row.get("status"));
        if ("published".equals(status) || "gray".equals(status)) {
            return Result.fail("正在生效的版本不能删除，先切到别的版本再删");
        }
        versions.remove(row);
        log.info("[占位] 删除版本（仅改内存）: id={}", id);
        return Result.success("删除成功（后端暂未实现持久化）");
    }
}
