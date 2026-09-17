package com.ai.cs.workorder.controller;

import com.ai.cs.common.result.PageResult;
import com.ai.cs.common.result.Result;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工单自定义字段与类型字典控制器
 *
 * <p>演示桩：返回内置假数据，尚未接入持久化。</p>
 *
 * @author huangrenhui
 */
@RestController
@RequestMapping("/workorder/field")
public class WorkOrderFieldController {

    /** 可用字段类型 */
    private static final List<Map<String, Object>> FIELD_TYPES = new ArrayList<>();

    /** 演示数据：自定义字段 */
    private static final List<Map<String, Object>> DEMO = new ArrayList<>();

    static {
        FIELD_TYPES.add(type("text", "单行文本"));
        FIELD_TYPES.add(type("textarea", "多行文本"));
        FIELD_TYPES.add(type("number", "数字"));
        FIELD_TYPES.add(type("select", "下拉单选"));
        FIELD_TYPES.add(type("date", "日期"));
        FIELD_TYPES.add(type("switch", "开关"));

        DEMO.add(row(1L, "orderNo", "关联订单号", "text", 1, 1, 10));
        DEMO.add(row(2L, "refundAmount", "退款金额", "number", 1, 1, 20));
        DEMO.add(row(3L, "refundReason", "退款原因", "select", 0, 1, 30));
        DEMO.add(row(4L, "expectedDate", "期望完成日期", "date", 0, 0, 40));
    }

    private static Map<String, Object> type(String code, String name) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", code);
        m.put("name", name);
        return m;
    }

    private static Map<String, Object> row(Long id, String fieldKey, String fieldName, String fieldType,
                                           int required, int status, int sortNum) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("fieldKey", fieldKey);
        m.put("fieldName", fieldName);
        m.put("fieldType", fieldType);
        m.put("required", required);
        m.put("status", status);
        m.put("sortNum", sortNum);
        return m;
    }

    /** 自定义字段分页列表 */
    @GetMapping("/page")
    public Result<PageResult<Map<String, Object>>> page(@RequestParam(defaultValue = "1") int pageNum,
                                                        @RequestParam(defaultValue = "10") int pageSize) {
        int from = Math.max(0, (pageNum - 1) * pageSize);
        int to = Math.min(DEMO.size(), from + pageSize);
        List<Map<String, Object>> records = from >= DEMO.size() ? List.of() : DEMO.subList(from, to);
        return Result.success(PageResult.of(records, DEMO.size(), pageNum, pageSize));
    }

    /** 全部自定义字段 */
    @GetMapping("/list")
    public Result<List<Map<String, Object>>> list() {
        return Result.success(DEMO);
    }

    /** 字段类型字典 */
    @GetMapping("/types")
    public Result<List<Map<String, Object>>> types() {
        return Result.success(FIELD_TYPES);
    }

    /** 新增字段（演示桩） */
    @PostMapping("/save")
    public Result<String> save(@RequestBody Map<String, Object> body) {
        return Result.success("新增成功（演示）");
    }

    /** 更新字段（演示桩） */
    @PutMapping("/update")
    public Result<String> update(@RequestBody Map<String, Object> body) {
        return Result.success("更新成功（演示）");
    }

    /** 删除字段（演示桩） */
    @DeleteMapping("/delete/{id}")
    public Result<String> delete(@PathVariable Long id) {
        return Result.success("删除成功（演示）");
    }
}
