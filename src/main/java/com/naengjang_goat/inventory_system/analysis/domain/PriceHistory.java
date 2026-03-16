package com.naengjang_goat.inventory_system.analysis.domain;

import com.naengjang_goat.inventory_system.inventory.domain.RawMaterial;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * [v2.1 비활성화]
 * 비활성화 사유: MarketPrice로 대체 (product_name 제거, ingredientId FK로 교체)
 * 재활성화 조건: 없음 (영구 대체)
 * 비활성화 일자: 2026-03-15
 */
// @Entity  // [v2.1 비활성화]
// @Table(name = "price_history")  // [v2.1 비활성화]
@Getter
@Setter
@NoArgsConstructor
public class PriceHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "raw_material_id")
    private RawMaterial rawMaterial;

    @Column(name = "price_date")
    private LocalDate priceDate;

    @Column(name = "product_name")
    private String productName;

    @Column(name = "unit")
    private String unit;

    @Column(name = "retail_price")
    private String retailPrice;

    @Column(name = "wholesale_price")
    private String wholesalePrice;
}
