package com.naengjang_goat.inventory_system.global.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * MockAuthFilter (v2.1)
 * Spring Security + JWT 대체 — X-User-Id 헤더 기반 인증
 *
 * 사용 방법:
 *   모든 요청 헤더에 X-User-Id: {userId} 포함
 *   예) curl -H "X-User-Id: 1" http://localhost:8080/api/...
 *
 * 재활성화 조건:
 *   실 배포 직전 JwtAuthenticationFilter + SecurityConfig 복구 시 이 필터 제거
 *
 * 비활성화 일자: 2026-03-15 (Spring Security 비활성화 시점과 동일)
 */
@Component
public class MockAuthFilter extends OncePerRequestFilter {

    public static final String USER_ID_HEADER = "X-User-Id";
    public static final String USER_ID_ATTRIBUTE = "userId";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String userIdHeader = request.getHeader(USER_ID_HEADER);

        if (userIdHeader == null || userIdHeader.isBlank()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"error\": \"X-User-Id 헤더가 없습니다.\"}");
            return;
        }

        try {
            Long userId = Long.parseLong(userIdHeader);
            request.setAttribute(USER_ID_ATTRIBUTE, userId);
        } catch (NumberFormatException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"error\": \"X-User-Id 헤더 형식이 올바르지 않습니다.\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }
}
