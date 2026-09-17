package com.ai.cs.base.controller;

import com.ai.cs.common.result.PageResult;
import com.ai.cs.common.result.Result;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 多租户管理控制器
 *
 * <p>演示桩：当前仅返回内置假数据，用于打通前端页面流程，尚未接入持久化。</p>
 *
 * @author huangrenhui
 */
@RestController
@RequestMapping("/system/tenant")
public class TenantController {

    /** 演示数据：已开通租户列表 */
    private static final List<Map<String, Object>> DEMO = new ArrayList<>();

    static {
        DEMO.add(row(1L, "default", "默认租户", "BASIC", 1, "2026-08-01 09:00:00", "2026-12-31 23:59:59"));
        DEMO.add(row(2L, "ecommerce", "电商旗舰店", "PRO", 1, "2026-08-15 10:00:00", "2027-08-15 23:59:59"));
        DEMO.add(row(3L, "finance", "金融事业部", "ENTERPRISE", 1, "2026-09-01 14:20:00", "2027-09-01 23:59:59"));
        DEMO.add(row(4L, "retail", "新零售试点", "TRIAL", 0, "2026-09-08 16:30:00", "2026-09-30 23:59:59"));
    }

    private static Map<String, Object> row(Long id, String code, String name, String plan,
                                           int status, String createTime, String expireTime) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("tenantCode", code);
        m.put("tenantName", name);
        m.put("planCode", plan);
        m.put("status", status);
        m.put("createTime", createTime);
        m.put("expireTime", expireTime);
        return m;
    }

    /** 租户分页列表 */
    @GetMapping("/page")
    public Result<PageResult<Map<String, Object>>> page(@RequestParam(defaultValue = "1") int pageNum,
                                                        @RequestParam(defaultValue = "10") int pageSize,
                                                        @RequestParam(required = false) String keyword) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> m : DEMO) {
            boolean hit = keyword == null || keyword.isBlank()
                    || String.valueOf(m.get("tenantName")).contains(keyword)
                    || String.valueOf(m.get("tenantCode")).contains(keyword);
            if (hit) {
                rows.add(m);
            }
        }
        int from = Math.max(0, (pageNum - 1) * pageSize);
        int to = Math.min(rows.size(), from + pageSize);
        List<Map<String, Object>> records = from >= rows.size() ? List.of() : rows.subList(from, to);
        return Result.success(PageResult.of(records, rows.size(), pageNum, pageSize));
    }

    /** 全部租户（下拉用） */
    @GetMapping("/list")
    public Result<List<Map<String, Object>>> list() {
        return Result.success(DEMO);
    }

    /** 新增租户（演示桩） */
    @PostMapping("/save")
    public Result<String> save(@RequestBody Map<String, Object> body) {
        return Result.success("新增成功（演示）");
    }

    /** 更新租户（演示桩） */
    @PutMapping("/update")
    public Result<String> update(@RequestBody Map<String, Object> body) {
        return Result.success("更新成功（演示）");
    }

    /** 启用 / 停用租户（演示桩） */
    @PutMapping("/{id}/status")
    public Result<String> status(@PathVariable Long id, @RequestParam int status) {
        return Result.success("状态已更新（演示）");
    }

    /** 删除租户（演示桩） */
    @DeleteMapping("/delete/{id}")
    public Result<String> delete(@PathVariable Long id) {
        return Result.success("删除成功（演示）");
    }
}
