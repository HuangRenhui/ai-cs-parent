package com.ai.cs.base.service;

import com.ai.cs.base.entity.Agent;
import com.ai.cs.base.mapper.AgentMapper;
import com.ai.cs.common.dto.LoginDTO;
import com.ai.cs.common.enums.AgentStatusEnum;
import com.ai.cs.common.exception.BusinessException;
import com.ai.cs.common.security.JwtContext;
import com.ai.cs.common.util.JwtUtil;
import com.ai.cs.common.util.ValidateUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 客服坐席业务逻辑
 *
 * <p>坐席数据按 {@code tenant_code} 隔离：列表/新增/更新/删除/状态变更全部以当前登录租户为准，
 * 令牌带租户时忽略请求参数里的租户，避免改一个参数就操作到别人的坐席。</p>
 *
 * @author huangrenhui
 */
@Service
public class AgentService extends ServiceImpl<AgentMapper, Agent> {

    /** 密码加密器（BCrypt） */
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /**
     * 查询本租户坐席列表（按创建时间倒序），返回前抹除密码字段避免泄露
     *
     * @param requestTenantCode 请求参数里的租户编码，仅平台级账号（令牌无租户）生效
     */
    public List<Agent> listAgents(String requestTenantCode) {
        String tenant = JwtContext.resolveTenantCode(requestTenantCode);
        List<Agent> list = this.list(new LambdaQueryWrapper<Agent>()
                .eq(Agent::getTenantCode, tenant)
                .orderByDesc(Agent::getCreateTime));
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

        // 令牌绑定租户时以令牌为准；平台级账号可按请求体指定租户
        String tenant = JwtContext.resolveTenantCode(agent.getTenantCode());
        agent.setTenantCode(tenant);
        if (agent.getId() != null) {
            // 更新前先确认目标坐席属于当前租户，否则「改别人的 id」就能跨租户写入
            Agent existing = this.getOne(new LambdaQueryWrapper<Agent>()
                    .eq(Agent::getId, agent.getId())
                    .eq(Agent::getTenantCode, tenant));
            if (existing == null) {
                throw new BusinessException("坐席不存在或无权操作");
            }
        }

        // 校验账号在租户内唯一；更新时排除自身
        LambdaQueryWrapper<Agent> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Agent::getTenantCode, tenant)
                .eq(Agent::getAgentAccount, agent.getAgentAccount());
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
            agent.setAgentPwd(encodePassword(agent.getAgentPwd()));
            this.save(agent);
        } else {
            // 更新：密码留空表示不修改，置 null 后由 NOT_NULL 策略跳过该字段；非空则重新加密
            if (ValidateUtil.trimToNull(agent.getAgentPwd()) == null) {
                agent.setAgentPwd(null);
            } else {
                agent.setAgentPwd(encodePassword(agent.getAgentPwd()));
            }
            // 逻辑删除标记不允许经更新接口改写
            agent.setDelFlag(null);
            this.updateById(agent);
        }
    }

    /** 密码统一编码入口：拒绝 BCrypt 密文入参，避免把已知哈希直接写库 */
    private String encodePassword(String rawPassword) {
        String password = ValidateUtil.trimToNull(rawPassword);
        if (password == null) {
            throw new BusinessException("密码不能为空");
        }
        if (password.startsWith("$2a$") || password.startsWith("$2b$") || password.startsWith("$2y$")
                || password.startsWith("$argon2")) {
            throw new BusinessException(400, "密码必须为明文，不能提交换密码文");
        }
        return passwordEncoder.encode(password);
    }

    /**
     * 平台登录：用对接平台的租户编码 + 坐席账号做身份校验，与管理员后台登录分离。
     *
     * @param dto 含租户编码、账号、密码；带 ssoTicket 时视为平台已完成单点校验
     * @return 坐席身份的登录结果（loginType=platform）
     */
    public LoginDTO.Result loginFromPlatform(LoginDTO dto) {
        if (dto == null || !StringUtils.hasText(dto.getTenantCode())) {
            throw new BusinessException("请填写对接平台的租户编码");
        }
        String tenant = dto.getTenantCode().trim();
        String account = ValidateUtil.trimToNull(dto.getUsername());
        if (account == null) {
            throw new BusinessException("请填写平台账号");
        }
        // 管理员账号禁止走平台入口，避免两套身份混在同一表单
        if ("admin".equalsIgnoreCase(account)) {
            throw new BusinessException("管理员请从「运营后台登录」入口进入");
        }
        // 账号必须属于所填租户：同一账号在不同租户下是两条独立记录
        Agent agent = this.getOne(new LambdaQueryWrapper<Agent>()
                .eq(Agent::getTenantCode, tenant)
                .eq(Agent::getAgentAccount, account)
                .last("limit 1"));
        if (agent == null) {
            throw new BusinessException("平台账号不存在或未同步到本系统");
        }
        // SSO 暂无可验证的签名/端点：仅凭 ssoTicket 非空就放行等于任意账号登录后门，一律拒绝
        if (StringUtils.hasText(dto.getSsoTicket())) {
            throw new BusinessException("SSO 登录未启用，请使用账号密码登录");
        }
        if (!StringUtils.hasText(dto.getPassword())
                || !StringUtils.hasText(agent.getAgentPwd())
                || !passwordEncoder.matches(dto.getPassword(), agent.getAgentPwd())) {
            throw new BusinessException("平台账号或密码错误");
        }
        // 坐席默认角色与权限点（后续可改为按坐席绑定的角色查库下发）
        List<String> roles = List.of("AGENT");
        List<String> permissions = List.of("chat:view", "session:view", "workorder:view");
        LoginDTO.Result result = new LoginDTO.Result();
        // 坐席令牌**绑定租户**：下游用 JwtContext.resolveTenantCode 取值时以令牌为准，
        // 这样即使有人改写请求参数里的 tenantCode 也读不到别的租户数据（防水平越权）
        result.setToken(JwtUtil.generateToken(agent.getId(), agent.getAgentAccount(), tenant, roles, permissions));
        result.setUsername(agent.getAgentAccount());
        result.setRealName(agent.getAgentName());
        result.setRoles(roles);
        result.setPermissions(permissions);
        result.setLoginType("platform");
        result.setTenantCode(tenant);
        return result;
    }

    /**
     * 更新坐席在线状态（仅限当前租户）
     *
     * @param id     坐席ID
     * @param status 目标状态（null 时按离线处理）
     */
    public void updateStatus(Long id, Integer status) {
        Agent agent = requireTenantAgent(id);
        agent.setAgentStatus(status == null ? AgentStatusEnum.OFFLINE.getCode() : status);
        this.updateById(agent);
    }

    /**
     * 删除坐席（逻辑删除，仅限当前租户）
     *
     * @param id 坐席ID
     */
    public void deleteAgent(Long id) {
        requireTenantAgent(id);
        this.removeById(id);
    }

    /**
     * 按「当前租户 + ID」取坐席。
     *
     * <p>不存在与不属于当前租户返回同一句提示，避免用 ID 探测其它租户的数据是否存在。</p>
     */
    private Agent requireTenantAgent(Long id) {
        String tenant = JwtContext.resolveTenantCode(null);
        Agent agent = this.getOne(new LambdaQueryWrapper<Agent>()
                .eq(Agent::getId, id)
                .eq(Agent::getTenantCode, tenant));
        if (agent == null) {
            throw new BusinessException("坐席不存在或无权操作");
        }
        return agent;
    }
}
