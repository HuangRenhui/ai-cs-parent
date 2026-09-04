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

@Service
public class AgentService extends ServiceImpl<AgentMapper, Agent> {

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public List<Agent> listAgents() {
        List<Agent> list = this.list(new LambdaQueryWrapper<Agent>().orderByDesc(Agent::getCreateTime));
        list.forEach(item -> item.setAgentPwd(null));
        return list;
    }

    public void saveAgent(Agent agent) {
        if (agent == null) {
            throw new BusinessException("坐席信息不能为空");
        }
        ValidateUtil.requireAccount(agent.getAgentAccount());
        ValidateUtil.requireDisplayName(agent.getAgentName());
        ValidateUtil.optionalPassword(agent.getAgentPwd(), agent.getId() == null);
        agent.setAgentAccount(ValidateUtil.trimToNull(agent.getAgentAccount()));
        agent.setAgentName(ValidateUtil.trimToNull(agent.getAgentName()));

        LambdaQueryWrapper<Agent> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Agent::getAgentAccount, agent.getAgentAccount());
        if (agent.getId() != null) {
            wrapper.ne(Agent::getId, agent.getId());
        }
        if (this.count(wrapper) > 0) {
            throw new BusinessException("坐席账号已存在");
        }
        if (agent.getAgentStatus() == null) {
            agent.setAgentStatus(AgentStatusEnum.OFFLINE.getCode());
        }
        if (agent.getId() == null) {
            agent.setAgentPwd(passwordEncoder.encode(ValidateUtil.trimToNull(agent.getAgentPwd())));
            this.save(agent);
        } else {
            if (ValidateUtil.trimToNull(agent.getAgentPwd()) == null) {
                agent.setAgentPwd(null);
            } else {
                agent.setAgentPwd(passwordEncoder.encode(agent.getAgentPwd().trim()));
            }
            this.updateById(agent);
        }
    }

    public void updateStatus(Long id, Integer status) {
        Agent agent = this.getById(id);
        if (agent == null) {
            throw new BusinessException("坐席不存在");
        }
        agent.setAgentStatus(status == null ? AgentStatusEnum.OFFLINE.getCode() : status);
        this.updateById(agent);
    }
}
