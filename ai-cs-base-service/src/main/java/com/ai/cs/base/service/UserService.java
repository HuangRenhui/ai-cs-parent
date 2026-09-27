package com.ai.cs.base.service;

import com.ai.cs.base.entity.Menu;
import com.ai.cs.base.entity.User;
import com.ai.cs.base.mapper.UserMapper;
import com.ai.cs.common.constant.RedisKeyConst;
import com.ai.cs.common.dto.LoginDTO;
import com.ai.cs.common.exception.BusinessException;
import com.ai.cs.common.security.SuperAdminAccess;
import com.ai.cs.common.util.JwtUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
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
        // 第 1 步：锁定检查。放在查库之前，锁定期内不做任何数据库/密码计算，避免被刷接口
        assertNotLocked(username);

        // 第 2 步：查账号，显式带上 del_flag=0，防止逻辑删除的账号仍能登录
        User user = this.getOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username)
                .eq(User::getDelFlag, 0));

        // 第 3 步：账号不存在与账号被禁用返回同一句提示（避免账号枚举），但都要累计失败次数
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
        // 超管兜底：菜单表（cs_menu.perms）可能还没配权限点，给通配权限 *:*:*，
        // 否则启用接口级权限校验（@RequirePermission）后超管自己也会被 403 挡住
        permissions = withSuperPermission(username, roles, permissions);

        // 生成Token：角色/权限随令牌下发，下游服务无需回查库；
        // 管理员是「平台级账号」（可跨租户运营），tenant 传 null 表示不绑定租户
        String token = JwtUtil.generateToken(user.getId(), user.getUsername(), null, roles, permissions);

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
     * 超管权限兜底：超管（SUPER_ADMIN 角色或种子账号 admin）始终补上通配权限 {@code *:*:*}。
     *
     * <p>为什么需要：权限点来自 {@code cs_menu.perms} 的菜单-角色关联，种子数据可能还没配全。
     * 一旦接口加了 {@code @RequirePermission}，权限点缺失会把超管自己挡在门外（403），
     * 而超管的语义本来就是「拥有全部权限」，因此这里做一次显式兜底。</p>
     *
     * @param username    登录名（种子账号 admin 即使未绑角色也算超管）
     * @param roles       角色编码列表
     * @param permissions 数据库中查出的权限标识
     * @return 兜底后的权限列表（非 null）
     */
    private List<String> withSuperPermission(String username, List<String> roles, List<String> permissions) {
        List<String> merged = permissions == null ? new ArrayList<>() : new ArrayList<>(permissions);
        if (SuperAdminAccess.isSuperAdmin(username, roles) && !merged.contains(JwtUtil.WILDCARD_PERMISSION)) {
            merged.add(JwtUtil.WILDCARD_PERMISSION);
        }
        return merged;
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
     *
     * <p>Redis 不可用时 <b>fail-open</b>（放行到后续密码校验），这是有意取舍：登录页是唯一入口，
     * 若因为缓存故障把所有人挡在门外，故障面会被放大；且此处不涉及越权，最坏情况只是少了限流。</p>
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
            throw e;   // 命中锁定：必须原样抛出，不能被下面的 catch 吞掉
        } catch (Exception e) {
            log.warn("读取登录锁定状态失败: {}", e.getMessage());
        }
    }

    /**
     * 密码错误累计失败次数，达到阈值写入锁定键。
     *
     * <p>计数是<b>滑动窗口</b>：每次失败都刷新过期时间（{@code expire}），所以「持续尝试」会一直续期，
     * 只有连续 {@code LOCK_MINUTES} 分钟没有失败尝试才会自动清零。</p>
     */
    private void recordFail(String username) {
        if (redisTemplate == null || username == null) {
            return;
        }
        try {
            String failKey = RedisKeyConst.LOGIN_FAIL + username;
            Long n = redisTemplate.opsForValue().increment(failKey);
            // 续期滑动窗口：窗口内每次失败都把计数有效期推后
            redisTemplate.expire(failKey, LOCK_MINUTES, TimeUnit.MINUTES);
            // 达到阈值写入独立的锁定键（与计数键分离，便于单独观察/解除）
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
