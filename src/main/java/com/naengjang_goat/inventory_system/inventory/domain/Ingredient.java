package com.naengjang_goat.inventory_system.inventory.domain;

import com.naengjang_goat.inventory_system.user.domain.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 원재료 마스터 엔티티 (v2.1)
 * 구 RawMaterial 대체 — base_unit, warning_threshold 추가
 * unitType enum 제거 → base_unit 문자열로 통합 (UnitConverter가 직접 참조)
 */
@Entity
@Table(name = "ingredient")
@Getter
@Setter
@NoArgsConstructor
public class Ingredient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String name; // 재료명 (예: 깐마늘, 파스타면)

    @Column(name = "base_unit", nullable = false)
    private String baseUnit; // 기본 관리 단위 (g, ml, 개) — UnitConverter 기준 단위

    @Column(name = "warning_threshold", precision = 10, scale = 3)
    private BigDecimal warningThreshold; // 최소 유지 재고량 — 이 수치 미만 시 발주 알림

    public Ingredient(User user, String name, String baseUnit, BigDecimal warningThreshold) {
        this.user = user;
        this.name = name;
        this.baseUnit = baseUnit;
        this.warningThreshold = warningThreshold;
    }
}
