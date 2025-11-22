package com.naengjang_goat.inventory_system.inventory.domain;

import com.naengjang_goat.inventory_system.recipe.domain.Recipe;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
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
