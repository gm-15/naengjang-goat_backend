package com.naengjang_goat.inventory_system.recipe.repository;

import com.naengjang_goat.inventory_system.recipe.domain.RecipeItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RecipeItemRepository extends JpaRepository<RecipeItem, Long> {

    // 특정 메뉴(레시피)의 재료 구성 얻기
    List<RecipeItem> findAllByRecipeId(Long recipeId);
}
