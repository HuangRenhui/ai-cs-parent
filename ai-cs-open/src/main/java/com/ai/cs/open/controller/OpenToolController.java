package com.ai.cs.open.controller;

import com.ai.cs.common.dto.ToolInvokeDTO;
import com.ai.cs.common.dto.ToolInvokeResultDTO;
import com.ai.cs.common.result.Result;
import com.ai.cs.open.entity.OpenTool;
import com.ai.cs.open.service.OpenPlatformService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;
import java.util.List;

/**
 * 开放工具管理控制器：工具注册的维护与工具调用入口。
 */
@RestController
@RequestMapping("/open")
public class OpenToolController {

    @Resource
    private OpenPlatformService openPlatformService;

    /**
     * 查询全部开放工具（按 ID 倒序）。
     */
    @GetMapping("/tool/list")
    public Result<List<OpenTool>> list() {
        return Result.success(openPlatformService.listTools());
    }

    /**
     * 新增工具；强制清空 ID，防止调用方伪装成更新。
     */
    @PostMapping("/tool/save")
    public Result<Void> save(@RequestBody OpenTool tool) {
        tool.setId(null);
        openPlatformService.saveTool(tool);
        return Result.success();
    }

    /**
     * 更新工具（按 ID 全量更新）。
     */
    @PutMapping("/tool/update")
    public Result<Void> update(@RequestBody OpenTool tool) {
        openPlatformService.saveTool(tool);
        return Result.success();
    }

    /**
     * 删除工具（逻辑删除）。
     */
    @DeleteMapping("/tool/delete/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        openPlatformService.deleteTool(id);
        return Result.success();
    }

    /**
     * 调用开放工具：走行业包开关 → 风险确认 → 幂等控制 → 连接器执行的完整链路。
     */
    @PostMapping("/tool/invoke")
    public Result<ToolInvokeResultDTO> invoke(@RequestBody ToolInvokeDTO dto) {
        return Result.success(openPlatformService.invoke(dto));
    }
}
