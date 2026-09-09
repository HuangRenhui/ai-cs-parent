package com.ai.cs.base.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security 安全配置
 * 主要禁用默认的表单登录和CSRF，使用JWT进行无状态认证
 *
 * @author huangrenhui
 * @date 2026/7/20
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /** 密码加密器 Bean（BCrypt 算法） */
    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 安全过滤链：无状态会话 + 关闭 CSRF；JWT 校验在网关完成，本服务默认放行
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // 认证接口和文档接口无需认证
                        .requestMatchers("/auth/login", "/auth/register", "/doc.html", "/webjars/**",
                                "/v3/api-docs/**", "/swagger-resources/**", "/swagger-ui/**").permitAll()
                        // 其他请求需要认证（Gateway已经做了JWT校验，这里默认放行）
                        .anyRequest().permitAll()
                )
                // 关闭 X-Frame-Options，便于内嵌 H2 控制台/文档页面等场景
                .headers(headers -> headers.frameOptions(frame -> frame.disable()));

        return http.build();
    }
}
