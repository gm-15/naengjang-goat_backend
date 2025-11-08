package com.naengjang_goat.inventory_system.recipe.repository;

import com.naengjang_goat.inventory_system.recipe.Recipe;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RecipeRepository extends JpaRepository<Recipe, Long> {

    // 점주별 레시피 조회 (User와 N:1 관계라고 가정)
    List<Recipe> findAllByUserId(Long userId);

    // 이름으로 검색 (선택)
    List<Recipe> findAllByNameContaining(String name);
}
