package com.ai.cs.common.security;

import com.ai.cs.common.exception.BusinessException;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * {@link RequirePermission} 切面：方法级权限校验，未登录或权限不足直接拒绝。
 */
@Aspect
@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class RequirePermissionAspect {

    /**
     * 执行目标方法前校验当前用户是否具备声明的权限标识。
     */
    @Before("@annotation(com.ai.cs.common.security.RequirePermission) || @within(com.ai.cs.common.security.RequirePermission)")
    public void check(JoinPoint joinPoint) {
        RequirePermission annotation = resolveAnnotation(joinPoint);
        if (annotation == null) {
            return;
        }
        Long userId = JwtContext.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException(401, "未登录或登录已过期");
        }
        if (!JwtContext.hasPermission(annotation.value())) {
            throw new BusinessException(403, "无权执行该操作");
        }
    }

    /** 优先取方法注解，其次取类注解 */
    private static RequirePermission resolveAnnotation(JoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        RequirePermission onMethod = method.getAnnotation(RequirePermission.class);
        if (onMethod != null) {
            return onMethod;
        }
        return joinPoint.getTarget().getClass().getAnnotation(RequirePermission.class);
    }
}
