package com.ai.cs.base.service;

import com.ai.cs.base.entity.Role;
import com.ai.cs.base.mapper.RoleMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 角色管理服务
 *
 * @author huangrenhui
 * @date 2026/7/20
 */
@Service
public class RoleService extends ServiceImpl<RoleMapper, Role> {

    /**
     * 获取所有启用的角色
     */
    public List<Role> getAllEnabledRoles() {
        return this.list(new LambdaQueryWrapper<Role>()
                .eq(Role::getStatus, 1)
                .eq(Role::getDelFlag, 0)
                .orderByAsc(Role::getSortNum));
    }
}
