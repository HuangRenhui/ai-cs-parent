package com.ai.cs.base.controller;

import com.ai.cs.base.entity.Agent;
import com.ai.cs.base.service.AgentService;
import com.ai.cs.common.exception.BusinessException;
import com.ai.cs.common.result.Result;
import org.springframework.web.bind.annotation.*;
import jakarta.annotation.Resource;
import java.util.List;
import java.util.Map;

/**
 * 客服坐席管理控制器
 *
 * @author huangrenhui
 */
@RestController
@RequestMapping("/agent")
public class AgentController {

    @Resource
    private AgentService agentService;

    /**
     * 坐席列表
     */
    @GetMapping("/list")
    public Result<List<Agent>> list() {
        return Result.success(agentService.listAgents());
    }

    /**
     * 新增坐席（强制清空 id，防止伪造更新请求）
     */
    @PostMapping("/save")
    public Result<String> save(@RequestBody Agent agent) {
        agent.setId(null);
        agentService.saveAgent(agent);
        return Result.success("新增成功");
    }

    /**
     * 更新坐席
     */
    @PutMapping("/update")
    public Result<String> update(@RequestBody Agent agent) {
        if (agent.getId() == null) {
            throw new BusinessException("坐席ID不能为空");
        }
        agentService.saveAgent(agent);
        return Result.success("修改成功");
    }

    /**
     * 更新坐席在线状态（上/下线、忙碌切换）
     */
    @PutMapping("/status/{id}")
    public Result<String> status(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        // body 允许为空，空时按离线处理
        Integer status = body == null ? null : body.get("agentStatus");
        agentService.updateStatus(id, status);
        return Result.success("状态已更新");
    }

    /**
     * 删除坐席（逻辑删除）
     */
    @DeleteMapping("/delete/{id}")
    public Result<String> delete(@PathVariable Long id) {
        if (agentService.getById(id) == null) {
            throw new BusinessException("坐席不存在");
        }
        agentService.removeById(id);
        return Result.success("删除成功");
    }
}
