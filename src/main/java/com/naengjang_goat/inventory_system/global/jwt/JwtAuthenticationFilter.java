package com.naengjang_goat.inventory_system.global.jwt;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 모든 요청에 대해 JWT 토큰을 검사하는 '보안 요원' 필터.
 * OncePerRequestFilter를 상속받아, 요청 당 단 한 번만 실행되도록 보장합니다.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final TokenProvider tokenProvider; // 우리가 만든 TokenProvider 주입

    /**
     * 실제 필터링 로직이 수행되는 곳
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // 1. 요청 헤더에서 "Authorization" 헤더를 찾고, 거기서 Access Token을 추출합니다.
        String token = resolveToken(request);

        // 2. 토큰이 존재하고, 유효성 검증(validateToken)에 성공한 경우에만
        //    이전에는 여기서 getUsernameFromToken을 호출했을 수 있습니다.
        if (token != null && tokenProvider.validateToken(token)) {

            // [수정된 로직]
            // 3. TokenProvider를 통해, 토큰에서 Authentication 객체를 가져옵니다.
            //    이 getAuthentication 메서드 안에는 DB에서 UserDetails를 조회하는 로직까지 포함됩니다.
            Authentication authentication = tokenProvider.getAuthentication(token);

            // 4. Spring Security의 '보안 컨텍스트'에 인증 정보를 저장합니다.
            //    이 코드가 실행되는 순간, Spring은 "이 사용자는 인증되었다"고 인식합니다.
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        // 5. 다음 필터로 요청/응답을 전달합니다.
        filterChain.doFilter(request, response);
    }

    /**
     * HttpServletRequest에서 'Authorization' 헤더를 추출하고,
     * 'Bearer ' 접두사를 제거하여 순수한 토큰 문자열을 반환합니다.
     * @param request
     * @return "Bearer "가 제거된 토큰 문자열, 또는 null
     */
    private String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7); // "Bearer " 이후의 문자열
        }
        return null;
    }
}

