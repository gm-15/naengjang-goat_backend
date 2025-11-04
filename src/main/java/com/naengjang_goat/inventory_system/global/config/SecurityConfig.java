package com.naengjang_goat.inventory_system.global.config;

import com.naengjang_goat.inventory_system.global.jwt.JwtAuthenticationFilter;
import com.naengjang_goat.inventory_system.global.security.UserDetailsServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security의 핵심 설정 파일.
 * 인증/인가 로직과 JWT 필터를 구성합니다.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final UserDetailsServiceImpl userDetailsService;
    private final JwtAuthenticationFilter jwtAuthenticationFilter; // 우리가 만든 JWT 필터 주입

    /**
     * 비밀번호 암호화를 위한 PasswordEncoder를 Bean으로 등록합니다.
     * BCrypt 알고리즘을 사용합니다.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * AuthenticationManager를 Bean으로 등록합니다.
     * UserService에서 로그인 시 인증을 처리하기 위해 사용됩니다.
     */
    @Bean
    public AuthenticationManager authenticationManager(HttpSecurity http) throws Exception {
        AuthenticationManagerBuilder authenticationManagerBuilder =
                http.getSharedObject(AuthenticationManagerBuilder.class);
        authenticationManagerBuilder
                .userDetailsService(userDetailsService) // DB에서 사용자를 찾아올 UserDetailsService 설정
                .passwordEncoder(passwordEncoder());    // 비밀번호 비교에 사용할 PasswordEncoder 설정
        return authenticationManagerBuilder.build();
    }

    /**
     * HTTP 요청에 대한 보안 필터 체인을 설정합니다.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // 1. CSRF 보호 비활성화 (JWT를 사용하므로 세션 기반의 CSRF 보호는 필요 없음)
                .csrf(AbstractHttpConfigurer::disable)

                // 2. 세션 관리 정책 설정: STATELESS (세션을 사용하지 않음)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                // 3. HTTP 요청 경로별 접근 권한 설정
                .authorizeHttpRequests(auth -> auth
                        // '/api/users/signup'과 '/api/users/login' 경로는 인증 없이 누구나 접근 허용
                        .requestMatchers("/api/users/signup", "/api/users/login").permitAll()
                        // 그 외의 모든 요청은 반드시 인증(로그인)을 거쳐야 함
                        .anyRequest().authenticated()
                )

                // [핵심] 4. 우리가 만든 JwtAuthenticationFilter를
                //            Spring Security의 기본 로그인 필터(UsernamePasswordAuthenticationFilter)보다
                //            한 단계 먼저 실행되도록 '배치'합니다.
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}

