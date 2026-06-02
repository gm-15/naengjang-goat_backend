package com.naengjang_goat.inventory_system.user.controller;

import com.naengjang_goat.inventory_system.global.security.CustomUserDetails;
import com.naengjang_goat.inventory_system.user.dto.OnboardRequest;
import com.naengjang_goat.inventory_system.user.dto.OnboardResponse;
import com.naengjang_goat.inventory_system.user.dto.TokenResponseDto;
import com.naengjang_goat.inventory_system.user.dto.UserLoginRequestDto;
import com.naengjang_goat.inventory_system.user.dto.UserSignupRequestDto;
import com.naengjang_goat.inventory_system.user.service.OnboardService;
import com.naengjang_goat.inventory_system.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * [v2.1 비활성화]
 * 비활성화 사유: MockAuthFilter로 인증 대체
 * 비활성화 일자: 2026-03-15
 */
@RestController  // [v2.1 재활성화 — JWT 인증 복구]
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final OnboardService onboardService;

    /**
     * 회원가입 API
     */
    @PostMapping("/signup")
    public ResponseEntity<String> signup(@RequestBody UserSignupRequestDto signupDto) {
        userService.signup(signupDto);
        return ResponseEntity.ok("회원가입이 성공적으로 완료되었습니다.");
    }

    /**
     * [신규] 로그인 API
     * 로그인을 성공하면, Body에 JWT(Access Token, Refresh Token)를 담아 반환합니다.
     */
    @PostMapping("/login")
    public ResponseEntity<TokenResponseDto> login(@RequestBody UserLoginRequestDto loginDto) {
        // 1. UserService의 login 메서드를 호출하여 인증을 시도하고,
        // 2. 성공 시 토큰(Access/Refresh)이 담긴 DTO를 받습니다.
        TokenResponseDto tokenResponse = userService.login(loginDto);

        // 3. HTTP 200 OK 상태와 함께 Body에 토큰 DTO를 담아 응답합니다.
        return ResponseEntity.ok(tokenResponse);
    }

    /**
     * POST /api/users/onboard
     * 가입 후 카테고리 선택 → 해당 카테고리 레시피 템플릿을 점주 메뉴로 자동 복사.
     *
     * Request:  { "categories": ["KOREAN", "WESTERN"] }
     * Response: { "createdMenus": 41, "createdBom": 850, "newIngredients": [...] }
     *
     * newIngredients: 기존 재료와 매칭 못 해 새로 생성된 재료명 목록.
     *                 requiredQuantity = 1 (placeholder) 로 생성되므로 직접 수정 필요.
     */
    @PostMapping("/onboard")
    public ResponseEntity<OnboardResponse> onboard(
            @AuthenticationPrincipal CustomUserDetails principal,
            @Valid @RequestBody OnboardRequest request) {
        OnboardResponse response = onboardService.onboard(principal.getId(), request);
        return ResponseEntity.ok(response);
    }

    /**
     * PATCH /api/users/fcm-token
     * 앱 실행 시 FCM 기기 토큰 등록/갱신.
     * Body: { "token": "FCM_DEVICE_TOKEN" }
     */
    @PatchMapping("/fcm-token")
    public ResponseEntity<Void> updateFcmToken(
            @AuthenticationPrincipal CustomUserDetails principal,
            @RequestBody java.util.Map<String, String> body) {
        userService.updateFcmToken(principal.getId(), body.get("token"));
        return ResponseEntity.noContent().build();
    }

    /**
     * POST /api/users/logout
     *
     * 현재 JWT 는 stateless — 서버 측 토큰 무효화 안 함.
     * 클라이언트가 access/refresh 토큰 폐기하면 됨.
     * 향후 보강: Redis 블랙리스트 (token jti 기준 5분 TTL 만료까지 거부).
     *
     * @author sim
     * @since 2026-06-01
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.noContent().build();
    }
}

