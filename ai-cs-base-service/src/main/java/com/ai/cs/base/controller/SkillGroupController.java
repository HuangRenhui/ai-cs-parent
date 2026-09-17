package com.ai.cs.base.controller;

import com.ai.cs.common.result.PageResult;
import com.ai.cs.common.result.Result;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 技能组与数据权限控制器
 *
 * <p>演示桩：返回内置假数据，尚未接入持久化与真实权限校验。</p>
 *
 * @author huangrenhui
 */
@RestController
@RequestMapping("/system/skill-group")
public class SkillGroupController {

    /** 可分配的数据范围字典 */
    private static final List<Map<String, Object>> DATA_SCOPES = new ArrayList<>();

    /** 演示数据：技能组 */
    private static final List<Map<String, Object>> DEMO = new ArrayList<>();

    static {
        DATA_SCOPES.add(scope("ALL", "全部数据"));
        DATA_SCOPES.add(scope("TENANT", "本租户数据"));
        DATA_SCOPES.add(scope("GROUP", "本技能组数据"));
        DATA_SCOPES.add(scope("SELF", "仅本人数据"));

        DEMO.add(row(1L, "GROUP_AFTER_SALE", "售后组", "售后 / 退款 / 物流", 3, 1, "GROUP"));
        DEMO.add(row(2L, "GROUP_PRESALE", "售前咨询组", "商品 / 下单 / 优惠", 2, 1, "GROUP"));
        DEMO.add(row(3L, "GROUP_FINANCE", "金融专席", "查账 / 挂失 / 分期", 2, 0, "TENANT"));
    }

    private static Map<String, Object> scope(String code, String name) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", code);
        m.put("name", name);
        return m;
    }

    private static Map<String, Object> row(Long id, String code, String name, String skillDesc,
                                           int agentCount, int status, String dataScope) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("groupCode", code);
        m.put("groupName", name);
        m.put("skillDesc", skillDesc);
        m.put("agentCount", agentCount);
        m.put("status", status);
        m.put("dataScope", dataScope);
        return m;
    }

    /** 技能组分页列表 */
    @GetMapping("/page")
    public Result<PageResult<Map<String, Object>>> page(@RequestParam(defaultValue = "1") int pageNum,
                                                        @RequestParam(defaultValue = "10") int pageSize) {
        int from = Math.max(0, (pageNum - 1) * pageSize);
        int to = Math.min(DEMO.size(), from + pageSize);
        List<Map<String, Object>> records = from >= DEMO.size() ? List.of() : DEMO.subList(from, to);
        return Result.success(PageResult.of(records, DEMO.size(), pageNum, pageSize));
    }

    /** 全部技能组（下拉用） */
    @GetMapping("/list")
    public Result<List<Map<String, Object>>> list() {
        return Result.success(DEMO);
    }

    /** 数据范围字典 */
    @GetMapping("/data-scopes")
    public Result<List<Map<String, Object>>> dataScopes() {
        return Result.success(DATA_SCOPES);
    }

    /** 新增技能组（演示桩） */
    @PostMapping("/save")
    public Result<String> save(@RequestBody Map<String, Object> body) {
        return Result.success("新增成功（演示）");
    }

    /** 更新技能组（演示桩） */
    @PutMapping("/update")
    public Result<String> update(@RequestBody Map<String, Object> body) {
        return Result.success("更新成功（演示）");
    }

    /** 调整数据权限（演示桩） */
    @PutMapping("/{id}/data-scope")
    public Result<String> dataScope(@PathVariable Long id, @RequestParam String dataScope) {
        return Result.success("数据权限已更新（演示）");
    }

    /** 删除技能组（演示桩） */
    @DeleteMapping("/delete/{id}")
    public Result<String> delete(@PathVariable Long id) {
        return Result.success("删除成功（演示）");
    }
}
