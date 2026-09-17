package com.naengjang_goat.inventory_system.purchase.domain;

import com.naengjang_goat.inventory_system.inventory.domain.Ingredient;
import com.naengjang_goat.inventory_system.user.domain.User;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 발주 이력 엔티티 (UC-SUP-8).
 *
 * totalAmount = quantity × unitPrice (서비스 레이어에서 계산 후 저장).
 * status 기본값 CONFIRMED (Demo에서 발주 즉시 확정).
 */
@Entity
@Table(name = "purchase_orders",
        indexes = {
                @Index(name = "idx_po_user_date", columnList = "user_id, ordered_at DESC"),
                @Index(name = "idx_po_ingredient", columnList = "ingredient_id")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PurchaseOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ingredient_id", nullable = false)
    private Ingredient ingredient;

    @Column(name = "ordered_at", nullable = false)
    private LocalDate orderedAt;

    @Column(nullable = false, precision = 10, scale = 3)
    private BigDecimal quantity;

    @Column(name = "base_unit", nullable = false, length = 20)
    private String baseUnit;

    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(nullable = false, length = 100)
    private String supplier;

    /**
     * Supplier 도메인 연결 — 신규 발주는 이 필드 사용 권장.
     * 기존 {@code supplier} String 은 호환을 위해 유지.
     * @author sim
     * @since 2026-06-04
     */
    @Column(name = "supplier_id")
    private Long supplierId;

    @Column(columnDefinition = "TEXT")
    private String memo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private PurchaseStatus status = PurchaseStatus.CONFIRMED;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
