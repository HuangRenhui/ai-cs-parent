package com.ai.cs.base.service;

import com.ai.cs.base.entity.Agent;
import com.ai.cs.base.mapper.AgentMapper;
import com.ai.cs.common.enums.AgentStatusEnum;
import com.ai.cs.common.exception.BusinessException;
import com.ai.cs.common.util.ValidateUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 客服坐席业务逻辑
 *
 * @author huangrenhui
 */
@Service
public class AgentService extends ServiceImpl<AgentMapper, Agent> {

    /** 密码加密器（BCrypt） */
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /**
     * 查询坐席列表（按创建时间倒序），返回前抹除密码字段避免泄露
     */
    public List<Agent> listAgents() {
        List<Agent> list = this.list(new LambdaQueryWrapper<Agent>().orderByDesc(Agent::getCreateTime));
        // 密码置空，防止密文随接口返回
        list.forEach(item -> item.setAgentPwd(null));
        return list;
    }

    /**
     * 新增或更新坐席
     *
     * @param agent 坐席信息（id 为空走新增，非空走更新）
     */
    public void saveAgent(Agent agent) {
        if (agent == null) {
            throw new BusinessException("坐席信息不能为空");
        }
        // 基础字段校验：账号/姓名必填，新增时密码必填、更新时密码可选
        ValidateUtil.requireAccount(agent.getAgentAccount());
        ValidateUtil.requireDisplayName(agent.getAgentName());
        ValidateUtil.optionalPassword(agent.getAgentPwd(), agent.getId() == null);
        agent.setAgentAccount(ValidateUtil.trimToNull(agent.getAgentAccount()));
        agent.setAgentName(ValidateUtil.trimToNull(agent.getAgentName()));

        // 校验账号唯一性；更新时排除自身
        LambdaQueryWrapper<Agent> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Agent::getAgentAccount, agent.getAgentAccount());
        if (agent.getId() != null) {
            wrapper.ne(Agent::getId, agent.getId());
        }
        if (this.count(wrapper) > 0) {
            throw new BusinessException("坐席账号已存在");
        }
        // 状态默认离线
        if (agent.getAgentStatus() == null) {
            agent.setAgentStatus(AgentStatusEnum.OFFLINE.getCode());
        }
        if (agent.getId() == null) {
            // 新增：密码加密后入库
            agent.setAgentPwd(passwordEncoder.encode(ValidateUtil.trimToNull(agent.getAgentPwd())));
            this.save(agent);
        } else {
            // 更新：密码留空表示不修改，置 null 后由 NOT_NULL 策略跳过该字段；非空则重新加密
            if (ValidateUtil.trimToNull(agent.getAgentPwd()) == null) {
                agent.setAgentPwd(null);
            } else {
                agent.setAgentPwd(passwordEncoder.encode(agent.getAgentPwd().trim()));
            }
            this.updateById(agent);
        }
    }

    /**
     * 更新坐席在线状态
     *
     * @param id     坐席ID
     * @param status 目标状态（null 时按离线处理）
     */
    public void updateStatus(Long id, Integer status) {
        Agent agent = this.getById(id);
        if (agent == null) {
            throw new BusinessException("坐席不存在");
        }
        agent.setAgentStatus(status == null ? AgentStatusEnum.OFFLINE.getCode() : status);
        this.updateById(agent);
    }
}
