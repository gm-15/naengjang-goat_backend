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

    @Column(name = "kamis_category", length = 20)
    private String kamisCategory; // KAMIS 품목 카테고리 (KamisCategory enum name). nullable, 기본 VEGETABLES

    @Column(name = "kamis_item_code", length = 10)
    private String kamisItemCode; // KAMIS item_code. 설정 시 이름 대신 코드로 매칭 (봄배추/고랭지배추 등 이름 변형 대응)

    /**
     * UI 노출용 카테고리 라벨 — "육류"|"채소"|"소스/양념"|"유제품"|"기타".
     * 프론트 {@code IngredientCategory} 와 1:1 매핑.
     * kamisCategory(KAMIS 분류)와는 별도. nullable.
     *
     * @since 2026-06-01 (sim, /prices/{id} 프론트 연동)
     */
    @Column(name = "category", length = 20)
    private String category;

    /**
     * 재료 대표 이미지 URL (시안 /lowest-price/{id} 상단 이미지).
     * 외부 CDN 또는 우리 정적 자산. nullable.
     *
     * @since 2026-06-01 (sim, /prices/{id} 프론트 연동)
     */
    @Column(name = "image_url", columnDefinition = "TEXT")
    private String imageUrl;

    public Ingredient(User user, String name, String baseUnit, BigDecimal warningThreshold) {
        this.user = user;
        this.name = name;
        this.baseUnit = baseUnit;
        this.warningThreshold = warningThreshold;
    }
}
