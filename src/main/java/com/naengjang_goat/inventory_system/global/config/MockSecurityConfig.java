package com.naengjang_goat.inventory_system.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * [v2.1 MockAuth 전용 Security 설정 — 비활성화]
 * JWT 인증 복구로 SecurityConfig로 대체됨.
 * PasswordEncoder, SecurityFilterChain Bean 은 SecurityConfig 에서 제공.
 */
// @Configuration  // [JWT 재활성화로 비활성화 — SecurityConfig 사용]
// @EnableWebSecurity
public class MockSecurityConfig {

    // @Bean — SecurityConfig.passwordEncoder() 와 충돌 방지
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // @Bean — SecurityConfig.securityFilterChain() 과 충돌 방지
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().permitAll()
                )
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable);

        return http.build();
    }
}
