package com.naengjang_goat.inventory_system.global.jwt;

import com.naengjang_goat.inventory_system.global.security.UserDetailsServiceImpl;
import com.naengjang_goat.inventory_system.user.dto.TokenResponseDto;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 토큰을 생성하고, 검증하고, 인증 정보를 추출하는 핵심 유틸리티 클래스
 * JJWT 라이브러리 0.12.x 버전에 최적화됨.
 */
@Component
@RequiredArgsConstructor
public class TokenProvider {

    private final UserDetailsServiceImpl userDetailsService;

    // application.properties에서 설정값 주입
    @Value("${jwt.secret}")
    private String secretString;

    @Value("${jwt.access-token-expiration-ms}")
    private long accessTokenExpirationMs;

    @Value("${jwt.refresh-token-expiration-ms}")
    private long refreshTokenExpirationMs;

    private SecretKey secretKey;

    /**
     * Bean이 생성된 후(@PostConstruct),
     * 주입받은 secretString을 HMAC-SHA 알고리즘에 맞는 SecretKey 객체로 변환합니다.
     */
    @PostConstruct
    public void init() {
        this.secretKey = Keys.hmacShaKeyFor(secretString.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * [UserService에서 호출할 핵심 메서드]
     * 인증 정보(Authentication)를 기반으로 Access Token과 Refresh Token을 생성합니다.
     * @param authentication Spring Security의 인증 정보 (로그인 성공한 사용자 정보)
     * @return Access/Refresh 토큰이 담긴 DTO
     */
    public TokenResponseDto createTokens(Authentication authentication) {
        String username = authentication.getName();
        Date now = new Date();

        // Access Token 생성
        String accessToken = Jwts.builder()
                .subject(username) // 토큰의 주체 (사용자 ID)
                .issuedAt(now) // 토큰 발급 시간
                .expiration(new Date(now.getTime() + accessTokenExpirationMs)) // 토큰 만료 시간
                .signWith(secretKey) // 사용할 암호화 키
                .compact(); // 문자열로 변환

        // Refresh Token 생성 (Access Token보다 긴 만료 시간 적용)
        String refreshToken = Jwts.builder()
                .subject(username)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + refreshTokenExpirationMs))
                .signWith(secretKey)
                .compact();

        return new TokenResponseDto(accessToken, refreshToken);
    }

    /**
     * JWT 토큰을 복호화(parsing)하여 토큰에 담긴 정보(Claims)를 꺼냅니다.
     * @param token 복호화할 토큰
     * @return 토큰의 payload에 해당하는 Claims
     */
    private Claims parseClaims(String token) {
        // Jwts.parser()가 0.12.x 버전의 새로운 빌더 방식입니다.
        return Jwts.parser()
                .verifyWith(secretKey) // 서명 검증에 사용할 키
                .build()
                .parseSignedClaims(token) // 토큰 파싱 및 검증
                .getPayload(); // payload(내용물) 반환
    }

    /**
     * [JwtAuthenticationFilter에서 사용할 핵심 메서드]
     * Access Token을 검증하고, 유효하다면 인증 정보(Authentication)를 반환합니다.
     * @param token 검증할 JWT 토큰
     * @return Spring Security의 Authentication 객체
     */
    public Authentication getAuthentication(String token) {
        Claims claims = parseClaims(token);

        // 토큰의 subject(username)를 기반으로 UserDetails를 DB에서 조회합니다.
        UserDetails userDetails = userDetailsService.loadUserByUsername(claims.getSubject());

        // Authentication 객체를 생성하여 반환합니다.
        return new UsernamePasswordAuthenticationToken(userDetails, "", userDetails.getAuthorities());
    }

    /**
     * [JwtAuthenticationFilter에서 사용할 핵심 메서드]
     * 토큰의 유효성을 검증합니다. (서명, 만료 시간 등)
     * @param token 검증할 JWT 토큰
     * @return 유효하면 true, 아니면 false
     */
    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (Exception e) {
            // (실제 운영 시 로그 추가: e.getMessage()) - 토큰 만료, 서명 오류, 잘못된 형식 등
            return false;
        }
    }
}

