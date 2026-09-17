package com.naengjang_goat.inventory_system.supplier.domain;

import com.naengjang_goat.inventory_system.user.domain.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 거래처 엔티티.
 *
 * 발주 이력의 거래처 정보를 정규화 — 이름·연락처·주소·메모를 재사용 가능한 단위로 분리.
 * 기존 {@code PurchaseOrder.supplier} (String) 와 별개로, neue {@link
 * com.naengjang_goat.inventory_system.purchase.domain.PurchaseOrder#supplierId} 를 통해 선택적 연결.
 *
 * 점주별 독립 (User 와 N:1). 같은 거래처라도 점주가 다르면 별도 row.
 *
 * @author sim
 * @since 2026-06-04
 */
@Entity
@Table(name = "supplier",
        indexes = {
                @Index(name = "idx_supplier_user_name", columnList = "user_id, name")
        })
@Getter
@Setter
@NoArgsConstructor
public class Supplier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** 거래처명 (예: "농협유통") */
    @Column(nullable = false, length = 100)
    private String name;

    /** 연락처 (예: "010-1234-5678") */
    @Column(length = 30)
    private String phone;

    /** 주소 */
    @Column(length = 255)
    private String address;

    /** 메모 — 최소 발주량·결제 조건 등 */
    @Column(columnDefinition = "TEXT")
    private String memo;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Supplier(User user, String name, String phone, String address, String memo) {
        this.user = user;
        this.name = name;
        this.phone = phone;
        this.address = address;
        this.memo = memo;
        this.createdAt = LocalDateTime.now();
    }
}
