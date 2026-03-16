package com.naengjang_goat.inventory_system.inventory.domain;

import com.naengjang_goat.inventory_system.recipe.domain.UnitType;
import com.naengjang_goat.inventory_system.user.domain.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * [v2.1 비활성화]
 * 비활성화 사유: Ingredient로 대체 (base_unit, warning_threshold 추가 / unitType enum 제거)
 * 재활성화 조건: 없음 (영구 대체)
 * 비활성화 일자: 2026-03-15
 *
 * 원재료를 정의하는 엔티티
 * 예: 깐마늘, 파스타면, 토마토소스
 */
// @Entity  // [v2.1 비활성화]
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

    public RawMaterial(String 토마토, UnitType unitType, User user) {
    }
}
