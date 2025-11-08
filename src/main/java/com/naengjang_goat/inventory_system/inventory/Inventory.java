package com.naengjang_goat.inventory_system.inventory;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * '원재료'의 '실시간 재고'를 관리하는 엔티티
 * '깐마늘'의 재고가 '5.2kg' 남았다.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class Inventory {

    @Id
    private Long id; // PK

    // RawMaterial과 1:1 관계
    // 재고(Inventory)는 원재료(RawMaterial)에 대한 부가정보이므로, PK를 공유하는 전략
    @OneToOne(fetch = FetchType.LAZY)
    @MapsId // RawMaterial의 ID를 이 엔티티의 PK로 사용
    @JoinColumn(name = "raw_material_id")
    private RawMaterial rawMaterial;

    @Column(nullable = false)
    private Double stockQuantity; // 현재 재고량 (예: 5.2)

    @Column(nullable = false)
    private String stockUnit; // 재고 관리 단위 (예: kg)
}
