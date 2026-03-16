package com.naengjang_goat.inventory_system.menu.domain;

import com.naengjang_goat.inventory_system.inventory.domain.Ingredient;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * BOM(Bill of Materials) 엔티티 (v2.1)
 * 구 RecipeItem 대체 — Double → BigDecimal, Ingredient FK로 교체
 * ERD 명칭: Recipe (BOM)
 *
 * unit 필드는 UnitConverter 동작 필수 — 누락 시 단위 변환 불가
 * 예: 메뉴 '제육볶음' 1인분에 '마늘' 50 'g' 필요
 */
@Entity
@Table(name = "recipe")
@Getter
@Setter
@NoArgsConstructor
public class RecipeBom {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "menu_id", nullable = false)
    private Menu menu;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ingredient_id", nullable = false)
    private Ingredient ingredient;

    @Column(name = "required_quantity", nullable = false, precision = 10, scale = 3)
    private BigDecimal requiredQuantity; // 소요량 — BigDecimal (구 Double 대체)

    @Column(nullable = false)
    private String unit; // 소요량 단위 (g, ml, 개) — UnitConverter 필수 참조 필드

    public RecipeBom(Menu menu, Ingredient ingredient, BigDecimal requiredQuantity, String unit) {
        this.menu = menu;
        this.ingredient = ingredient;
        this.requiredQuantity = requiredQuantity;
        this.unit = unit;
    }
}
