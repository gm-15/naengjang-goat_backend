package com.naengjang_goat.inventory_system.analysis.domain;

import com.naengjang_goat.inventory_system.inventory.domain.RawMaterial; // recipe 패키지의 RawMaterial 참조
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDate;

/**
 * '원재료'의 '일별 시세'를 저장하는 엔티티
 * '깐마늘'이 '2025-10-31'에 '4200원/kg'이었다.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class PriceHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 어떤 원재료(RawMaterial)의 가격인지
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "raw_material_id")
    private RawMaterial rawMaterial;

    @Column(nullable = false)
    private LocalDate priceDate; // 가격 기준일

    @Column(nullable = false)
    private Integer price; // 평균 가격

    @Column(nullable = false)
    private String priceUnit; // 가격 단위 (예: 1kg당)
}
