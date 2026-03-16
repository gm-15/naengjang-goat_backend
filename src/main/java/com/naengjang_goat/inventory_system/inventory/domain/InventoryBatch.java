package com.naengjang_goat.inventory_system.inventory.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 입고 배치 엔티티 (v2.1 신규)
 * 구 Inventory(단순 수량) 대체 — FIFO 소진 + 유통기한 추적 + 원가 분석
 *
 * 총 재고량 계산: SELECT SUM(quantity) FROM inventory_batch WHERE ingredient_id = ?
 * FIFO 소진 기준: ORDER BY expiration_date ASC
 * D-3 알림 기준: expiration_date <= NOW() + INTERVAL 3 DAY
 *
 * 동시성 제어 핵심 대상: quantity 필드 (LockStrategy 적용 대상)
 */
@Entity
@Table(name = "inventory_batch")
@Getter
@Setter
@NoArgsConstructor
public class InventoryBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ingredient_id", nullable = false)
    private Ingredient ingredient;

    @Column(nullable = false, precision = 10, scale = 3)
    private BigDecimal quantity; // 현재 남은 수량 — 동시성 제어 핵심 대상

    @Column(name = "cost_per_unit", precision = 10, scale = 2)
    private BigDecimal costPerUnit; // 입고 단가 — 원가 계산 및 ROI 분석용

    @Column(name = "inbound_date", nullable = false)
    private LocalDate inboundDate; // 입고 날짜

    @Column(name = "expiration_date", nullable = false)
    private LocalDate expirationDate; // 유통기한 — FIFO 정렬 기준

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public InventoryBatch(Ingredient ingredient, BigDecimal quantity,
                          BigDecimal costPerUnit, LocalDate inboundDate, LocalDate expirationDate) {
        this.ingredient = ingredient;
        this.quantity = quantity;
        this.costPerUnit = costPerUnit;
        this.inboundDate = inboundDate;
        this.expirationDate = expirationDate;
        this.createdAt = LocalDateTime.now();
    }
}
