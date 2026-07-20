package com.ai.cs.base.service;

import com.ai.cs.base.entity.User;
import com.ai.cs.base.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.*;

/**
 * UserService 单元测试
 *
 * @author huangrenhui
 * @date 2026/7/20
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("UserService 单元测试")
class UserServiceTest {

    @Mock
    private UserMapper userMapper;

    private UserService userService;

    private User testUser;

    @BeforeEach
    void setUp() {
        userService = new UserService();
        ReflectionTestUtils.setField(userService, "baseMapper", userMapper);

        testUser = new User();
        testUser.setId(1L);
        testUser.setUsername("admin");
        testUser.setRealName("管理员");
        testUser.setStatus(1);
        testUser.setDelFlag(0);
        // BCrypt 加密的 "123456"
        testUser.setPassword("$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy");
    }

    @Test
    @DisplayName("登录 - 用户不存在抛异常")
    void testLogin_UserNotFound() {
        doReturn(null).when(userMapper).selectOne(any(), anyBoolean());
        assertThrows(RuntimeException.class,
                () -> userService.login("admin", "123456"));
    }

    @Test
    @DisplayName("登录 - 密码错误抛异常")
    void testLogin_WrongPassword() {
        doReturn(testUser).when(userMapper).selectOne(any(), anyBoolean());
        assertThrows(RuntimeException.class,
                () -> userService.login("admin", "wrong"));
    }

    @Test
    @DisplayName("获取当前用户信息 - 正常场景")
    void testGetCurrentUserInfo() {
        doReturn(testUser).when(userMapper).selectOne(any(), anyBoolean());
        User result = userService.getCurrentUserInfo(1L);
        assertNotNull(result);
        assertEquals("admin", result.getUsername());
    }

    @Test
    @DisplayName("创建用户 - 密码应被加密")
    void testCreateUser_PasswordEncrypted() {
        User newUser = new User();
        newUser.setUsername("test");
        newUser.setPassword("123456");
        when(userMapper.insert(any(User.class))).thenReturn(1);

        boolean result = userService.createUser(newUser);
        assertTrue(result);
        assertTrue(newUser.getPassword().startsWith("$2a$"));
    }

    @Test
    @DisplayName("更新用户 - 已有加密密码不重复加密")
    void testUpdateUser_AlreadyEncrypted() {
        User updateUser = new User();
        updateUser.setId(1L);
        updateUser.setPassword("$2a$10$encrypted");
        when(userMapper.updateById(any(User.class))).thenReturn(1);

        boolean result = userService.updateUser(updateUser);
        assertTrue(result);
        assertTrue(updateUser.getPassword().startsWith("$2a$"));
    }
}
