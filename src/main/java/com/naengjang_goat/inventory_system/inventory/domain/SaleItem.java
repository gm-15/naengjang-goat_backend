package com.naengjang_goat.inventory_system.inventory.domain;

import com.naengjang_goat.inventory_system.recipe.domain.Recipe;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * [v2.1 비활성화]
 * 비활성화 사유: OrderItem으로 재설계 (unit_price 스냅샷 추가, FK 정리)
 * 재활성화 조건: 없음 (영구 대체)
 * 비활성화 일자: 2026-03-15
 */
// @Entity  // [v2.1 비활성화]
@Getter
@Setter
@NoArgsConstructor
public class SaleItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 영수증(판매 히스토리) 1 : N
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sale_history_id")
    private SaleHistory saleHistory;

    // 어떤 메뉴가 팔렸는지
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipe_id")
    private Recipe recipe;

    @Column(nullable = false)
    private int quantitySold;

    public SaleItem(SaleHistory saleHistory, Recipe recipe, int quantitySold) {
        this.saleHistory = saleHistory;
        this.recipe = recipe;
        this.quantitySold = quantitySold;
    }
}
