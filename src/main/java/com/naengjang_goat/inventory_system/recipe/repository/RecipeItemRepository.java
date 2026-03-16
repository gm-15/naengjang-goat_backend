package com.naengjang_goat.inventory_system.recipe.repository;

import com.naengjang_goat.inventory_system.recipe.domain.RecipeItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

import java.util.List;

/**
 * [v2.1 비활성화]
 * 비활성화 사유: RecipeBomRepository로 대체
 * 비활성화 일자: 2026-03-15
 */
@NoRepositoryBean
public interface RecipeItemRepository extends JpaRepository<RecipeItem, Long> {

    // 특정 메뉴(레시피)의 재료 구성 얻기
    List<RecipeItem> findAllByRecipeId(Long recipeId);
}
