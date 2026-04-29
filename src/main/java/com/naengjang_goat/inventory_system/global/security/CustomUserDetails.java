package com.naengjang_goat.inventory_system.global.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.util.Collection;

/**
 * JWT 인증 후 SecurityContext에 저장되는 사용자 상세 정보.
 *
 * 기본 Spring User에 userId(DB PK)를 추가해 컨트롤러에서
 * @AuthenticationPrincipal CustomUserDetails 로 바로 userId를 꺼낼 수 있게 함.
 *
 * 사용 예:
 *   @GetMapping("/low-stock")
 *   public ResponseEntity<?> list(@AuthenticationPrincipal CustomUserDetails principal) {
 *       Long userId = principal.getId();
 *       ...
 *   }
 */
public class CustomUserDetails extends User {

    private final Long id;

    public CustomUserDetails(Long id, String username, String password,
                             Collection<? extends GrantedAuthority> authorities) {
        super(username, password, authorities);
        this.id = id;
    }

    /** DB 기본 키 (users.id) */
    public Long getId() {
        return id;
    }
}
