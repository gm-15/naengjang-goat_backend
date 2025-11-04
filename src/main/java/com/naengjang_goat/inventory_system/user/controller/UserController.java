package com.naengjang_goat.inventory_system.user.controller;

import com.naengjang_goat.inventory_system.user.dto.TokenResponseDto;
import com.naengjang_goat.inventory_system.user.dto.UserLoginRequestDto;
import com.naengjang_goat.inventory_system.user.dto.UserSignupRequestDto;
import com.naengjang_goat.inventory_system.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

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
}

