package com.naengjang_goat.inventory_system.menu.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 식자재왕 레시피 공용 템플릿 (sim 크롤러 적재).
 *
 * 점주(user) 소유 데이터가 아닌 공용 라이브러리.
 * POST /api/users/onboard 시 이 테이블 → Menu + RecipeBom 복사.
 *
 * category 값: KOREAN / WESTERN / CHINESE / JAPANESE / OTHER
 */
@Entity
@Table(name = "recipe_template")
@Getter
@NoArgsConstructor
public class RecipeTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String category;

    @Column(name = "menu_name", nullable = false, length = 100)
    private String menuName;

    @Column(nullable = false, length = 20)
    private String source;

    @Column(name = "source_recipe_idx", nullable = false)
    private Integer sourceRecipeIdx;

    @Column(name = "image_url", columnDefinition = "TEXT")
    private String imageUrl;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "template", fetch = FetchType.LAZY)
    private List<RecipeTemplateBom> bomList = new ArrayList<>();
}
