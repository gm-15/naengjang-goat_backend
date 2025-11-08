package com.naengjang_goat.inventory_system.user.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "users") // 'user'는 DB 예약어인 경우가 많아 'users'를 권장합니다.
@Getter
@Setter
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username; // 로그인 ID

    @Column(nullable = false)
    private String password; // Spring Security로 암호화될 필드

    @Column(nullable = false)
    private String ownerName; // 점주 이름

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role; // 권한 (점주, 직원 등)
    private boolean active = true;
}
