package com.naengjang_goat.inventory_system.recipe.domain;

import com.naengjang_goat.inventory_system.inventory.domain.RawMaterial;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 레시피와 원재료의 '관계'를 정의하는 '연결 엔티티'
 * N:M 관계를 1:N, N:1로 풀어내는 핵심
 * 예: '토마토 스파게티' 1인분은 '파스타면' '100g'이 필요하다.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class RecipeItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 이 RecipeItem은 어떤 Recipe에 속해있는가? (N:1)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipe_id")
    private Recipe recipe;

    // 이 RecipeItem은 어떤 RawMaterial을 사용하는가? (N:1)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "raw_material_id")
    private RawMaterial rawMaterial;

    @Column(nullable = false)
    private Double quantity; // 소모량 (예: 100.0)

    @Column(nullable = false)
    private String unit; // 소모 단위 (예: g, ml, 개)
}
