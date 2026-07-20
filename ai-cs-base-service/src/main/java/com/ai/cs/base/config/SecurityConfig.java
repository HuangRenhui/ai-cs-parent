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

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

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
                .headers(headers -> headers.frameOptions(frame -> frame.disable()));

        return http.build();
    }
}
