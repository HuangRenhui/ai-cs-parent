package com.ai.cs.base.service;

import com.ai.cs.base.entity.Menu;
import com.ai.cs.base.entity.User;
import com.ai.cs.base.mapper.UserMapper;
import com.ai.cs.common.constant.RedisKeyConst;
import com.ai.cs.common.dto.LoginDTO;
import com.ai.cs.common.exception.BusinessException;
import com.ai.cs.common.util.JwtUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 用户认证与权限服务
 *
 * @author huangrenhui
 * @date 2026/7/20
 */
@Slf4j
@Service
public class UserService extends ServiceImpl<UserMapper, User> {

    /** 连续失败次数达到该值后锁定账号 */
    private static final int MAX_FAIL = 5;
    /** 锁定时长（分钟） */
    private static final long LOCK_MINUTES = 15;

    /** 密码加密器（BCrypt） */
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    /**
     * 用户登录：先查锁定 → 校验账号状态与密码 → 成功清失败计数，失败累加并可能锁定。
     */
    public LoginDTO.Result login(String username, String password) {
        assertNotLocked(username);

        User user = this.getOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username)
                .eq(User::getDelFlag, 0));

        if (user == null || user.getStatus() == 0) {
            recordFail(username);
            throw new BusinessException("用户不存在或已被禁用");
        }

        if (!passwordEncoder.matches(password, user.getPassword())) {
            recordFail(username);
            throw new BusinessException("用户名或密码错误");
        }

        clearFail(username);

        // 获取角色和权限
        List<String> roles = baseMapper.selectRolesByUserId(user.getId());
        List<String> permissions = baseMapper.selectPermissionsByUserId(user.getId());
        assertAdminEntrance(username, roles);

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
        result.setLoginType("admin");
        return result;
    }

    /**
     * 管理员入口只允许超管/管理员角色；种子账号 admin 在尚未绑角色时也放行。
     */
    private void assertAdminEntrance(String username, List<String> roles) {
        boolean namedAdmin = username != null && "admin".equalsIgnoreCase(username.trim());
        boolean roleAdmin = roles != null && roles.stream().anyMatch(r ->
                r != null && (r.equalsIgnoreCase("SUPER_ADMIN")
                        || r.equalsIgnoreCase("ADMIN")
                        || r.contains("管理")));
        if (!namedAdmin && !roleAdmin) {
            throw new BusinessException("该账号不是管理员，请从「普通用户登录」入口进入");
        }
    }

    /**
     * 锁定期内直接拒绝，不暴露「是否存在该用户」。
     */
    private void assertNotLocked(String username) {
        if (redisTemplate == null || username == null) {
            return;
        }
        try {
            String locked = redisTemplate.opsForValue().get(RedisKeyConst.LOGIN_LOCK + username);
            if (locked != null) {
                throw new BusinessException("登录失败次数过多，请 " + LOCK_MINUTES + " 分钟后再试");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("读取登录锁定状态失败: {}", e.getMessage());
        }
    }

    /** 密码错误累计失败次数，达到阈值写入锁定键 */
    private void recordFail(String username) {
        if (redisTemplate == null || username == null) {
            return;
        }
        try {
            String failKey = RedisKeyConst.LOGIN_FAIL + username;
            Long n = redisTemplate.opsForValue().increment(failKey);
            redisTemplate.expire(failKey, LOCK_MINUTES, TimeUnit.MINUTES);
            if (n != null && n >= MAX_FAIL) {
                redisTemplate.opsForValue().set(RedisKeyConst.LOGIN_LOCK + username, "1", LOCK_MINUTES, TimeUnit.MINUTES);
                log.warn("账号已锁定 username={} fail={}", username, n);
            }
        } catch (Exception e) {
            log.warn("记录登录失败次数失败: {}", e.getMessage());
        }
    }

    /** 登录成功清除失败计数与锁定 */
    private void clearFail(String username) {
        if (redisTemplate == null || username == null) {
            return;
        }
        try {
            redisTemplate.delete(RedisKeyConst.LOGIN_FAIL + username);
            redisTemplate.delete(RedisKeyConst.LOGIN_LOCK + username);
        } catch (Exception e) {
            log.debug("清除登录失败计数失败: {}", e.getMessage());
        }
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
