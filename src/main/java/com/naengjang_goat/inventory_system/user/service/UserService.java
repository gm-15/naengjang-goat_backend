package com.naengjang_goat.inventory_system.user.service;

import com.naengjang_goat.inventory_system.global.jwt.TokenProvider;
import java.util.NoSuchElementException;
import com.naengjang_goat.inventory_system.user.domain.Role;
import com.naengjang_goat.inventory_system.user.dto.TokenResponseDto;
import com.naengjang_goat.inventory_system.user.dto.UserLoginRequestDto;
import com.naengjang_goat.inventory_system.user.dto.UserSignupRequestDto;
import com.naengjang_goat.inventory_system.user.domain.User;
import com.naengjang_goat.inventory_system.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * User의 비즈니스 로직(회원가입, 로그인)을 처리하는 서비스 클래스
 */
/**
 * [v2.1 비활성화]
 * 비활성화 사유: MockAuthFilter로 인증 대체, PasswordEncoder/AuthenticationManager/TokenProvider 의존성 제거
 * 비활성화 일자: 2026-03-15
 */
@Service  // [v2.1 재활성화 — JWT 인증 복구]
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final TokenProvider tokenProvider;

    /**
     * 회원가입 로직
     * @param signupDto 회원가입 요청 DTO
     * @return 저장된 User 엔티티
     */
    @Transactional // 쓰기 작업이므로 별도 트랜잭션 설정
    public User signup(UserSignupRequestDto signupDto) {
        // 1. 아이디 중복 검사
        if (userRepository.findByUsername(signupDto.getUsername()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 사용중인 아이디입니다.");
        }

        // 2. 비밀번호 암호화
        String encodedPassword = passwordEncoder.encode(signupDto.getPassword());

        // 3. User 객체 생성 및 권한 설정
        User user = new User();
        user.setUsername(signupDto.getUsername());
        user.setPassword(encodedPassword);
        user.setOwnerName(signupDto.getOwnerName());

        // [중요] 403 오류 해결: 모든 신규 가입자에게 기본 'OWNER' 권한 부여
        user.setRole(Role.OWNER);

        // 4. DB에 저장
        return userRepository.save(user);
    }

    /**
     * FCM 토큰의 현재 소유자를 갱신한다. 같은 기기로 다른 계정에 로그인하면 이전 계정의
     * 연결을 해제하며, null은 현재 계정의 연결만 해제한다.
     */
    @Transactional
    public void updateFcmToken(Long userId, String fcmToken) {
        updateFcmToken(userId, fcmToken, null);
    }

    @Transactional
    public void updateFcmToken(Long userId, String fcmToken, String expectedToken) {
        if (fcmToken == null) {
            User user = userRepository.lockById(userId)
                    .orElseThrow(() -> new NoSuchElementException("사용자 없음: " + userId));
            // 다른 기기가 더 최근에 등록한 토큰은 이전 기기의 로그아웃으로 해제하지 않는다.
            if (expectedToken != null && !expectedToken.equals(user.getFcmToken())) return;
            user.setFcmToken(null);
            return;
        }
        var owners = userRepository.lockFcmTokenOwners(userId, fcmToken);
        User user = owners.stream().filter(owner -> owner.getId().equals(userId)).findFirst()
                .orElseThrow(() -> new NoSuchElementException("사용자 없음: " + userId));
        for (User previousOwner : owners) {
            if (!previousOwner.getId().equals(userId)) previousOwner.setFcmToken(null);
        }
        // 새 소유자 ID가 더 작아도 고유 인덱스에 충돌하지 않도록 이전 연결부터 반영한다.
        // 이후 등록이 실패하면 이 해제까지 같은 트랜잭션으로 롤백된다.
        userRepository.flush();
        user.setFcmToken(fcmToken);
    }

    /**
     * 로그인 로직
     * @param loginDto 로그인 요청 DTO
     * @return AccessToken과 RefreshToken이 담긴 DTO
     */
    @Transactional
    public TokenResponseDto login(UserLoginRequestDto loginDto) {
        // 1. Spring Security의 AuthenticationManager를 사용하여 사용자 인증 시도
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            loginDto.getUsername(),
                            loginDto.getPassword()
                    )
            );
        } catch (AuthenticationException e) {
            // 아이디 없음 · 비밀번호 불일치 → 401 (프론트가 "아이디 또는 비밀번호" 안내)
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "아이디 또는 비밀번호가 올바르지 않습니다.");
        }

        // 2. 인증에 성공했다면, TokenProvider를 사용하여 JWT 토큰 생성
        TokenResponseDto tokenResponseDto = tokenProvider.createTokens(authentication);

        // 3. (선택적) Refresh Token을 DB에 저장하는 로직을 추가할 수 있습니다.
        //    (예: user.updateRefreshToken(tokenResponseDto.getRefreshToken());)

        // 4. 토큰 반환
        return tokenResponseDto;
    }
}

