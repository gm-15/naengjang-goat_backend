package com.naengjang_goat.inventory_system.menu.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 식자재왕 레시피 템플릿 BOM (재료 구성).
 *
 * 주의: quantity 는 1인분 소모량이 아닌 상품 패키지 단위 무게(g).
 * onboard 시 RecipeBom.requiredQuantity = 1 (placeholder) 로 복사.
 * 사장님이 직접 수정해야 정확한 원가 계산 가능.
 */
@Entity
@Table(name = "recipe_template_bom")
@Getter
@NoArgsConstructor
public class RecipeTemplateBom {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id", nullable = false)
    private RecipeTemplate template;

    @Column(name = "ingredient_name", nullable = false)
    private String ingredientName;

    private Integer quantity;

    @Column(length = 10)
    private String unit;

    @Column(name = "raw_product_gno", length = 64)
    private String rawProductGno;

    @Column(name = "product_price")
    private Integer productPrice;

    @Column(name = "is_discount", nullable = false)
    private boolean isDiscount;
}
