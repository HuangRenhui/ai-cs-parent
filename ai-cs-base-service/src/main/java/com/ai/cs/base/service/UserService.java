package com.ai.cs.base.service;

import com.ai.cs.base.entity.Menu;
import com.ai.cs.base.entity.User;
import com.ai.cs.base.mapper.UserMapper;
import com.ai.cs.common.dto.LoginDTO;
import com.ai.cs.common.exception.BusinessException;
import com.ai.cs.common.util.JwtUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 用户认证与权限服务
 *
 * @author huangrenhui
 * @date 2026/7/20
 */
@Slf4j
@Service
public class UserService extends ServiceImpl<UserMapper, User> {

    /** 密码加密器（BCrypt） */
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /**
     * 用户登录
     */
    public LoginDTO.Result login(String username, String password) {
        User user = this.getOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username)
                .eq(User::getDelFlag, 0));

        if (user == null || user.getStatus() == 0) {
            throw new BusinessException("用户不存在或已被禁用");
        }

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new BusinessException("用户名或密码错误");
        }

        // 获取角色和权限
        List<String> roles = baseMapper.selectRolesByUserId(user.getId());
        List<String> permissions = baseMapper.selectPermissionsByUserId(user.getId());

        // 生成Token
        String token = JwtUtil.generateToken(user.getId(), user.getUsername());

        // 更新最后登录时间
        user.setLastLoginTime(LocalDateTime.now());
        this.updateById(user);

        LoginDTO.Result result = new LoginDTO.Result();
        result.setToken(token);
        result.setUsername(user.getUsername());
        result.setRealName(user.getRealName());
        result.setRoles(roles);
        result.setPermissions(permissions);
        return result;
    }

    /**
     * 获取当前用户信息
     */
    public User getCurrentUserInfo(Long userId) {
        return this.getOne(new LambdaQueryWrapper<User>()
                .eq(User::getId, userId)
                .eq(User::getDelFlag, 0));
    }

    /**
     * 获取用户菜单树
     */
    public List<Menu> getUserMenus(Long userId) {
        return baseMapper.selectMenusByUserId(userId);
    }

    /**
     * 获取用户角色
     */
    public List<String> getUserRoles(Long userId) {
        return baseMapper.selectRolesByUserId(userId);
    }

    /**
     * 获取用户权限
     */
    public List<String> getUserPermissions(Long userId) {
        return baseMapper.selectPermissionsByUserId(userId);
    }

    /**
     * 创建用户（密码加密）
     */
    public boolean createUser(User user) {
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        return this.save(user);
    }

    /**
     * 更新用户（如果密码有变化则加密）
     */
    public boolean updateUser(User user) {
        // 密码非空且不是 BCrypt 密文（$2a$ 前缀）时视为新密码，需重新加密；
        // 空密码/密文则原样保留，避免重复加密导致密文被二次加密
        if (user.getPassword() != null && !user.getPassword().isBlank()
                && !user.getPassword().startsWith("$2a$")) {
            user.setPassword(passwordEncoder.encode(user.getPassword()));
        }
        return this.updateById(user);
    }
}
