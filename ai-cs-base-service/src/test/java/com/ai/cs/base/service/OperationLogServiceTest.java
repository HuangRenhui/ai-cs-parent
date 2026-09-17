package com.ai.cs.base.service;

import com.ai.cs.base.mapper.OperationLogMapper;
import com.ai.cs.base.mapper.UserMapper;
import com.ai.cs.common.exception.BusinessException;
import com.ai.cs.common.security.JwtContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * 操作审计删除权限：只有超级管理员能删。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OperationLogService")
class OperationLogServiceTest {

    @Mock
    private OperationLogMapper operationLogMapper;
    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private OperationLogService service;

    @AfterEach
    void clear() {
        JwtContext.clear();
    }

    @Test
    @DisplayName("运营管理员不能删除操作审计")
    void operatorCannotDelete() {
        JwtContext.setCurrentUser(2L, "operator");
        when(userMapper.selectRolesByUserId(2L)).thenReturn(List.of("ADMIN"));
        BusinessException ex = assertThrows(BusinessException.class, () -> service.deleteById(1L));
        assertEquals(403, ex.getCode());
        assertFalse(service.currentUserIsSuperAdmin());
    }

    @Test
    @DisplayName("SUPER_ADMIN 视为超管")
    void superRoleIsAdmin() {
        JwtContext.setCurrentUser(9L, "boss");
        when(userMapper.selectRolesByUserId(9L)).thenReturn(List.of("SUPER_ADMIN"));
        assertTrue(service.currentUserIsSuperAdmin());
    }
}
