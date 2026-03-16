package com.naengjang_goat.inventory_system.analysis.domain;

import com.naengjang_goat.inventory_system.inventory.domain.Ingredient;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * KAMIS 시세 데이터 엔티티 (v2.1)
 * 구 PriceHistory 대체 — product_name 제거, Ingredient FK로 교체
 *
 * retail_price / wholesale_price 분리 유지
 * → KAMIS API 원본이 두 값 제공
 * → 도매가 기준 발주 시점 추천에 필요 (Phase 3 AI 추천)
 * String 타입 유지: KAMIS가 "1,234" 형식으로 반환하기 때문
 */
@Entity
@Table(name = "market_price")
@Getter
@Setter
@NoArgsConstructor
public class MarketPrice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ingredient_id", nullable = false)
    private Ingredient ingredient;

    @Column(name = "retail_price")
    private String retailPrice; // KAMIS 소매가 (예: "2,500")

    @Column(name = "wholesale_price")
    private String wholesalePrice; // KAMIS 도매가 (예: "1,800")

    @Column(name = "unit")
    private String unit; // 시세 기준 단위

    @Column(name = "reported_date", nullable = false)
    private LocalDate reportedDate; // 시세 공표일

    public MarketPrice(Ingredient ingredient, String retailPrice, String wholesalePrice,
                       String unit, LocalDate reportedDate) {
        this.ingredient = ingredient;
        this.retailPrice = retailPrice;
        this.wholesalePrice = wholesalePrice;
        this.unit = unit;
        this.reportedDate = reportedDate;
    }
}
