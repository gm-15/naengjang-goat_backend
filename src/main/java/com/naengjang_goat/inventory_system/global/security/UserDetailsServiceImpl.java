package com.naengjang_goat.inventory_system.global.security;

import com.naengjang_goat.inventory_system.user.domain.User;
import com.naengjang_goat.inventory_system.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;

/**
 * [v2.1 비활성화]
 * 비활성화 사유: MockAuthFilter로 대체
 * 재활성화 조건: 실 배포 직전 Spring Security + JWT 복구 시
 * 비활성화 일자: 2026-03-15
 *
 * Spring Security의 UserDetailsService 인터페이스를 구현한 '사용자 검색기' 클래스.
 * AuthenticationManager가 인증을 수행할 때 이 클래스를 사용하여 DB에서 사용자를 조회합니다.
 */
@Service  // [v2.1 재활성화 — JWT 인증 복구]
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    /**
     * username(로그인 ID)을 기반으로 DB에서 사용자를 찾아 CustomUserDetails 객체로 변환합니다.
     * CustomUserDetails 에 userId(DB PK)가 포함돼 컨트롤러에서 바로 꺼낼 수 있습니다.
     *
     * @param username 로그인 시도 시 입력된 사용자 ID
     * @return CustomUserDetails (userId, username, password, authorities)
     * @throws UsernameNotFoundException 해당 username을 가진 사용자가 DB에 없을 경우
     */
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다. : " + username));

        return new CustomUserDetails(
                user.getId(),
                user.getUsername(),
                user.getPassword(),
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))
        );
    }
}

