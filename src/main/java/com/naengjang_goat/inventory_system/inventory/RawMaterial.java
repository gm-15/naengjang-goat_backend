package com.naengjang_goat.inventory_system.inventory;

import com.naengjang_goat.inventory_system.recipe.UnitType;
import com.naengjang_goat.inventory_system.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 원재료를 정의하는 엔티티
 * 예: 깐마늘, 파스타면, 토마토소스
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class RawMaterial {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name; // 재료명 (예: 깐마늘)

    // 이 재료의 기본 단위가 무게인지, 부피인지, 개수인지 구분
    @Enumerated(EnumType.STRING)
    private UnitType unitType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;
}
