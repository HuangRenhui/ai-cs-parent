package com.ai.cs.common.security;

import java.lang.annotation.*;

/**
 * 权限校验注解
 * 标注在方法上表示需要特定权限才能访问
 *
 * @author huangrenhui
 * @date 2026/7/20
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequirePermission {
    /** 权限标识，如 "system:user:list" */
    String value();
}
