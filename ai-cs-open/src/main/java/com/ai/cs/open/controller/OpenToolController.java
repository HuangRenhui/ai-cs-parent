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

@RestController
@RequestMapping("/open")
public class OpenToolController {

    @Resource
    private OpenPlatformService openPlatformService;

    @GetMapping("/tool/list")
    public Result<List<OpenTool>> list() {
        return Result.success(openPlatformService.listTools());
    }

    @PostMapping("/tool/save")
    public Result<Void> save(@RequestBody OpenTool tool) {
        tool.setId(null);
        openPlatformService.saveTool(tool);
        return Result.success();
    }

    @PutMapping("/tool/update")
    public Result<Void> update(@RequestBody OpenTool tool) {
        openPlatformService.saveTool(tool);
        return Result.success();
    }

    @DeleteMapping("/tool/delete/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        openPlatformService.deleteTool(id);
        return Result.success();
    }

    @PostMapping("/tool/invoke")
    public Result<ToolInvokeResultDTO> invoke(@RequestBody ToolInvokeDTO dto) {
        return Result.success(openPlatformService.invoke(dto));
    }
}
