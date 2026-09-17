package com.ai.cs.open.controller;

import com.ai.cs.common.result.Result;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Widget 隐私同意与白标配置控制器（占位实现）。
 *
 * <p>支持多套配置：不同租户、不同渠道（官网 / 活动页 / 小程序）可各配一套，
 * 品牌名、主题色与隐私声明互不影响；同一时刻只有「启用」的那套对外生效。</p>
 *
 * <p><b>当前状态</b>：内存桩，数据存在本地 List 里，<b>服务重启即丢</b>，仅用于跑通前后端契约。</p>
 *
 * <p><b>TODO 后续实现</b>：
 * <ol>
 *   <li>建表 {@code cs_widget_config}（tenant_code 隔离），字段见 docs 的接口约定</li>
 *   <li>同一租户下是否允许多套同时启用需产品确认；当前约定「允许多套启用，按排序号取第一个」</li>
 *   <li>{@code GET /get} 是对外初始化接口，响应里不要带后台管理字段（enabled / sortNum）</li>
 *   <li>删除正在生效的配置时，需确认客户端能正确回退到其它启用配置</li>
 * </ol>
 *
 * <p>注：本模块未引入 Swagger 依赖，接口说明沿用 Javadoc，与模块内其它 Controller 保持一致。</p>
 *
 * @author huangrenhui
 */
@Slf4j
@RestController
@RequestMapping("/open/widget-config")
public class OpenWidgetConfigController {

    /** 内存配置表，重启即丢 */
    private final List<Map<String, Object>> configs = new ArrayList<>();
    private final AtomicLong idSeq = new AtomicLong(10);

    @PostConstruct
    void seed() {
        configs.add(build(1L, "官网默认", "default", 1, 0,
                "智能客服", "#2f6bff", "/vite.svg", "您好，我是智能小客，请问有什么可以帮您？", "right", 1));
        configs.add(build(2L, "电商旗舰店", "ecommerce", 1, 10,
                "电商旗舰店客服", "#ff6a00", "/vite.svg", "亲，欢迎光临～有问题随时找我。", "right", 1));
        configs.add(build(3L, "金融事业部", "finance", 0, 20,
                "金融在线客服", "#0f9d58", "/vite.svg", "您好，请通过身份核验后咨询。", "left", 0));
    }

    private Map<String, Object> build(Long id, String configName, String tenantCode, int enabled, int sortNum,
                                      String brandName, String brandColor, String logoUrl, String welcomeText,
                                      String position, int allowAttachment) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("configName", configName);
        m.put("tenantCode", tenantCode);
        m.put("enabled", enabled);
        m.put("sortNum", sortNum);
        m.put("brandName", brandName);
        m.put("brandColor", brandColor);
        m.put("logoUrl", logoUrl);
        m.put("welcomeText", welcomeText);
        m.put("position", position);
        m.put("allowAttachment", allowAttachment);
        m.put("privacyEnabled", 1);
        m.put("privacyTitle", "隐私与数据使用说明");
        m.put("privacyText", "为提供在线客服服务，我们会在您同意后收集会话内容，用于问题定位与服务改进。");
        m.put("privacyAgreeText", "我已阅读并同意");
        return m;
    }

    private Map<String, Object> findById(Object id) {
        return configs.stream()
                .filter(x -> Objects.equals(String.valueOf(x.get("id")), String.valueOf(id)))
                .findFirst().orElse(null);
    }

    /**
     * 配置列表（后台维护用）。
     *
     * <p>按排序号升序返回全部配置，含停用项。</p>
     *
     * @return 白标配置列表（当前为内存演示数据）
     */
    @GetMapping("/list")
    public Result<List<Map<String, Object>>> list() {
        List<Map<String, Object>> rows = new ArrayList<>(configs);
        rows.sort(Comparator.comparingInt(a -> (int) a.get("sortNum")));
        return Result.success(rows);
    }

    /**
     * 读取当前生效的 Widget 配置（对外初始化用）。
     *
     * <p>取「启用」配置里排序号最小的那套；一套都没有则退回第一套。</p>
     *
     * @return 生效的白标配置
     */
    @GetMapping("/get")
    public Result<Map<String, Object>> get() {
        return Result.success(configs.stream()
                .filter(x -> Integer.valueOf(1).equals(x.get("enabled")))
                .min(Comparator.comparingInt(x -> (int) x.get("sortNum")))
                .orElse(configs.isEmpty() ? new LinkedHashMap<>() : configs.get(0)));
    }

    /**
     * 新增白标配置（占位实现）。
     *
     * <p>TODO 后续实现：configName 必填且同租户内唯一后落库。</p>
     *
     * @param body 配置内容（configName / tenantCode / 品牌与隐私字段）
     * @return 处理结果说明
     */
    @PostMapping("/save")
    public Result<String> save(@RequestBody Map<String, Object> body) {
        if (isBlank(body.get("configName"))) {
            return Result.fail("请填写配置名称");
        }
        log.info("[占位] 新增白标配置（未持久化）: configName={}, tenantCode={}",
                body.get("configName"), body.get("tenantCode"));

        Map<String, Object> row = new LinkedHashMap<>(body);
        row.put("id", idSeq.incrementAndGet());
        row.putIfAbsent("tenantCode", "");
        row.put("enabled", toInt(body.get("enabled")));
        row.put("sortNum", toInt(body.get("sortNum")));
        configs.add(row);
        return Result.success("保存成功（后端暂未实现持久化）");
    }

    /**
     * 编辑白标配置（占位实现）。
     *
     * @param body 配置内容，按 id 定位
     * @return 处理结果说明
     */
    @PutMapping("/update")
    public Result<String> update(@RequestBody Map<String, Object> body) {
        Map<String, Object> row = findById(body.get("id"));
        if (row == null) {
            return Result.fail("配置不存在");
        }
        if (isBlank(body.get("configName"))) {
            return Result.fail("请填写配置名称");
        }
        log.info("[占位] 编辑白标配置（未持久化）: id={}", body.get("id"));
        row.putAll(body);
        return Result.success("保存成功（后端暂未实现持久化）");
    }

    /**
     * 启用/停用白标配置（占位实现）。
     *
     * @param id      配置ID
     * @param enabled 1 启用 0 停用
     * @return 处理结果说明
     */
    @PutMapping("/{id}/enabled")
    public Result<String> setEnabled(@PathVariable Long id, @RequestParam Integer enabled) {
        Map<String, Object> row = findById(id);
        if (row == null) {
            return Result.fail("配置不存在");
        }
        row.put("enabled", enabled == null ? 0 : enabled);
        log.info("[占位] 启停白标配置（仅改内存）: id={}, enabled={}", id, enabled);
        return Result.success(enabled != null && enabled == 1 ? "已启用（后端暂未真正生效）" : "已停用（后端暂未真正生效）");
    }

    /**
     * 删除白标配置（占位实现）。
     *
     * <p>TODO 后续实现：删除生效中的配置前，确认客户端能回退到其它启用项。</p>
     *
     * @param id 配置ID
     * @return 处理结果说明
     */
    @DeleteMapping("/delete/{id}")
    public Result<String> delete(@PathVariable Long id) {
        Map<String, Object> row = findById(id);
        if (row == null) {
            return Result.fail("配置不存在");
        }
        configs.remove(row);
        log.info("[占位] 删除白标配置（仅改内存）: id={}", id);
        return Result.success("删除成功（后端暂未实现持久化）");
    }

    private static boolean isBlank(Object o) {
        return o == null || String.valueOf(o).isBlank();
    }

    private static int toInt(Object o) {
        if (o instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(o));
        } catch (Exception e) {
            return 0;
        }
    }
}
