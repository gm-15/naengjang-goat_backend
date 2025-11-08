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
 * Spring Security의 UserDetailsService 인터페이스를 구현한 '사용자 검색기' 클래스.
 * AuthenticationManager가 인증을 수행할 때 이 클래스를 사용하여 DB에서 사용자를 조회합니다.
 */
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    /**
     * username(로그인 ID)을 기반으로 DB에서 사용자를 찾아 Spring Security의 UserDetails 객체로 변환합니다.
     * @param username 로그인 시도 시 입력된 사용자 ID
     * @return Spring Security가 인증에 사용할 UserDetails 객체
     * @throws UsernameNotFoundException 해당 username을 가진 사용자가 DB에 없을 경우
     */
    @Override
    @Transactional(readOnly = true) // 로그인 시에는 데이터를 수정하지 않으므로 읽기 전용으로 설정
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // 1. UserRepository를 사용해 DB에서 사용자를 조회합니다.
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다. : " + username));

        // 2. Spring Security가 이해할 수 있는 UserDetails 객체로 변환하여 반환합니다.
        return new org.springframework.security.core.userdetails.User(
                user.getUsername(),
                user.getPassword(),
                // 사용자의 권한(Role)을 Spring Security가 인식할 수 있는 GrantedAuthority 형태로 변환합니다.
                // "ROLE_" 접두사는 Spring Security의 기본 규칙입니다.
                // [수정] "ROLE_"R + -> "ROLE_" + 로 오타 수정
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))
        );
    }
}

