package com.ai.cs.base.controller;

import com.ai.cs.base.entity.Agent;
import com.ai.cs.base.service.AgentService;
import com.ai.cs.common.exception.BusinessException;
import com.ai.cs.common.result.Result;
import org.springframework.web.bind.annotation.*;
import jakarta.annotation.Resource;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/agent")
public class AgentController {

    @Resource
    private AgentService agentService;

    @GetMapping("/list")
    public Result<List<Agent>> list() {
        return Result.success(agentService.listAgents());
    }

    @PostMapping("/save")
    public Result<String> save(@RequestBody Agent agent) {
        agent.setId(null);
        agentService.saveAgent(agent);
        return Result.success("新增成功");
    }

    @PutMapping("/update")
    public Result<String> update(@RequestBody Agent agent) {
        if (agent.getId() == null) {
            throw new BusinessException("坐席ID不能为空");
        }
        agentService.saveAgent(agent);
        return Result.success("修改成功");
    }

    @PutMapping("/status/{id}")
    public Result<String> status(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        Integer status = body == null ? null : body.get("agentStatus");
        agentService.updateStatus(id, status);
        return Result.success("状态已更新");
    }

    @DeleteMapping("/delete/{id}")
    public Result<String> delete(@PathVariable Long id) {
        if (agentService.getById(id) == null) {
            throw new BusinessException("坐席不存在");
        }
        agentService.removeById(id);
        return Result.success("删除成功");
    }
}
