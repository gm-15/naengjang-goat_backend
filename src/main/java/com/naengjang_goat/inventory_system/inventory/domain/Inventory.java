package com.naengjang_goat.inventory_system.inventory.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * [v2.1 비활성화]
 * 비활성화 사유: InventoryBatch로 대체 (FIFO + 유통기한 관리)
 * 총 재고량 = InventoryBatch.quantity SUM으로 계산
 * 재활성화 조건: 없음 (영구 대체)
 * 비활성화 일자: 2026-03-15
 */
// @Entity  // [v2.1 비활성화]
@Getter
@Setter
@NoArgsConstructor
public class Inventory {

    @Id
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "raw_material_id")
    private RawMaterial rawMaterial;

    @Column(nullable = false)
    private Double stockQuantity;

    @Column(nullable = false)
    private String stockUnit;

    public Inventory(RawMaterial rawMaterial, double stockQuantity) {
        this.rawMaterial = rawMaterial;
        this.stockQuantity = stockQuantity;
    }
}
