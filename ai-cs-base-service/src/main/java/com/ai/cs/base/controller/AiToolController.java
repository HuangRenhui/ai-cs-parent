package com.ai.cs.base.controller;

import com.ai.cs.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * AI 工具注册控制器（占位实现）
 *
 * <p>AI 工具箱原先的工具清单硬编码在前端 catalog 里，无法新增。
 * 这里提供一套「工具注册」接口，让自定义工具可以通过接口动态创建，
 * 并支持把工具执行绑定到外部 HTTP 开放接口。</p>
 *
 * <p>当前仅提供接口壳，具体实现待补齐：
 * <ul>
 *   <li>建表 cs_ai_tool（租户隔离）并做 CRUD</li>
 *   <li>工具编码唯一性校验、内置工具保护</li>
 *   <li>HTTP 类型工具的连通性探测与调用鉴权</li>
 * </ul>
 *
 * @author huangrenhui
 * @date 2026-09-14
 */
@Slf4j
@RestController
@RequestMapping("/system/ai-tool")
@Tag(name = "AI 工具注册", description = "AI 工具箱自定义工具注册与开放接口绑定（占位实现）")
public class AiToolController {

    /**
     * 查询自定义工具列表（占位）
     *
     * <p>TODO 后续实现：按租户从 cs_ai_tool 查询自定义工具，内置工具仍由前端 catalog 提供。</p>
     *
     * @param tenantCode 租户编码
     * @return 自定义工具列表（当前恒为空）
     */
    @GetMapping("/list")
    @Operation(summary = "查询自定义 AI 工具列表", description = "当前为占位实现，恒返回空列表")
    public Result<List<Map<String, Object>>> list(
            @Parameter(description = "租户编码") @RequestParam(required = false) String tenantCode) {
        log.info("[占位] 查询自定义 AI 工具: tenantCode={}", tenantCode);
        return Result.success(List.of());
    }

    /**
     * 新建或更新自定义工具（占位）
     *
     * <p>TODO 后续实现：校验编码唯一后落库，并返回持久化后的工具。</p>
     *
     * @param tool 工具定义（code/name/category/summary/execType/apiUrl 等）
     * @return 处理结果说明
     */
    @PostMapping("/save")
    @Operation(summary = "新建或更新自定义工具", description = "当前为占位实现，不会持久化")
    public Result<String> save(@RequestBody Map<String, Object> tool) {
        log.info("[占位] 收到工具注册请求（未持久化）: code={}, name={}, execType={}",
                tool.get("code"), tool.get("name"), tool.get("execType"));
        return Result.success("工具定义已接收（后端暂未实现持久化）");
    }

    /**
     * 删除自定义工具（占位）
     *
     * <p>TODO 后续实现：逻辑删除，内置工具应拒绝删除。</p>
     *
     * @param id 工具ID
     * @return 处理结果说明
     */
    @DeleteMapping("/delete/{id}")
    @Operation(summary = "删除自定义工具", description = "当前为占位实现，不会真正删除")
    public Result<String> delete(@Parameter(description = "工具ID") @PathVariable Long id) {
        log.info("[占位] 收到删除工具请求（未生效）: id={}", id);
        return Result.success("删除请求已接收（后端暂未实现）");
    }

    /**
     * 启用/停用自定义工具（占位）
     *
     * <p>TODO 后续实现：更新启用状态，停用后工作台不再可进入。</p>
     *
     * @param id 工具ID
     * @param body 请求体，enabled 为目标状态
     * @return 处理结果说明
     */
    @PutMapping("/enabled/{id}")
    @Operation(summary = "启用或停用自定义工具", description = "当前为占位实现，不会生效")
    public Result<String> setEnabled(@Parameter(description = "工具ID") @PathVariable Long id,
                                     @RequestBody(required = false) Map<String, Object> body) {
        log.info("[占位] 收到启停请求（未生效）: id={}, enabled={}", id, body == null ? null : body.get("enabled"));
        return Result.success("状态变更请求已接收（后端暂未实现）");
    }

    /**
     * 测试工具绑定的开放接口连通性（占位）
     *
     * <p>TODO 后续实现：按 apiUrl + httpMethod 发起探测请求，回传状态码与耗时。</p>
     *
     * @param id 工具ID
     * @return 处理结果说明
     */
    @PostMapping("/test/{id}")
    @Operation(summary = "测试自定义工具绑定的开放接口", description = "当前为占位实现，不会真正发起调用")
    public Result<String> test(@Parameter(description = "工具ID") @PathVariable Long id) {
        log.info("[占位] 收到工具连通性测试请求（未发起调用）: id={}", id);
        return Result.success("连通性测试未实现（后端占位）");
    }
}
