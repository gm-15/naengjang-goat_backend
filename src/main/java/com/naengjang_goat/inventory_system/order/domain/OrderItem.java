package com.naengjang_goat.inventory_system.order.domain;

import com.naengjang_goat.inventory_system.menu.domain.Menu;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 주문 상세 엔티티 (v2.1)
 * 구 SaleItem 재설계 — N:M(Order:Menu) 해소 테이블
 *
 * unit_price: 판매 시점 menu.price 스냅샷
 *   → 이후 메뉴 가격이 변경돼도 당시 판매가 보존
 */
@Entity
@Table(name = "order_item")
@Getter
@Setter
@NoArgsConstructor
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "menu_id", nullable = false)
    private Menu menu;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "unit_price", nullable = false)
    private Integer unitPrice; // 판매 시점 가격 스냅샷

    public OrderItem(Order order, Menu menu, Integer quantity) {
        this.order = order;
        this.menu = menu;
        this.quantity = quantity;
        this.unitPrice = menu.getPrice(); // 생성 시점 가격 고정
    }
}
