package com.ai.cs.common.security;

import java.lang.annotation.*;

/**
 * 无需认证注解
 * 标注在方法或类上表示该接口不需要JWT认证
 *
 * @author huangrenhui
 * @date 2026/7/20
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface NoAuth {
}
