package com.naengjang_goat.inventory_system.user.service;

import com.naengjang_goat.inventory_system.global.jwt.TokenProvider;
import com.naengjang_goat.inventory_system.user.Role;
import com.naengjang_goat.inventory_system.user.dto.TokenResponseDto;
import com.naengjang_goat.inventory_system.user.dto.UserLoginRequestDto;
import com.naengjang_goat.inventory_system.user.dto.UserSignupRequestDto;
import com.naengjang_goat.inventory_system.user.User;
import com.naengjang_goat.inventory_system.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * User의 비즈니스 로직(회원가입, 로그인)을 처리하는 서비스 클래스
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true) // 기본적으로 읽기 전용 트랜잭션 설정
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
            throw new IllegalArgumentException("이미 사용중인 아이디입니다.");
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
     * 로그인 로직
     * @param loginDto 로그인 요청 DTO
     * @return AccessToken과 RefreshToken이 담긴 DTO
     */
    @Transactional
    public TokenResponseDto login(UserLoginRequestDto loginDto) {
        // 1. Spring Security의 AuthenticationManager를 사용하여 사용자 인증 시도
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        loginDto.getUsername(),
                        loginDto.getPassword()
                )
        );
        // (인증에 실패하면 여기서 401 Unauthorized 예외가 발생함)

        // 2. 인증에 성공했다면, TokenProvider를 사용하여 JWT 토큰 생성
        TokenResponseDto tokenResponseDto = tokenProvider.createTokens(authentication);

        // 3. (선택적) Refresh Token을 DB에 저장하는 로직을 추가할 수 있습니다.
        //    (예: user.updateRefreshToken(tokenResponseDto.getRefreshToken());)

        // 4. 토큰 반환
        return tokenResponseDto;
    }
}

