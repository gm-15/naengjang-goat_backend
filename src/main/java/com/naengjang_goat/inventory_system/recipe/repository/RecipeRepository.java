package com.naengjang_goat.inventory_system.recipe.repository;

import com.naengjang_goat.inventory_system.recipe.domain.Recipe;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

import java.util.List;

/**
 * [v2.1 비활성화]
 * 비활성화 사유: MenuRepository로 대체 (Recipe 클래스가 Menu로 변경됨)
 * 비활성화 일자: 2026-03-15
 */
@NoRepositoryBean
public interface RecipeRepository extends JpaRepository<Recipe, Long> {

    // 점주별 레시피 조회 (User와 N:1 관계라고 가정)
    List<Recipe> findAllByUserId(Long userId);

    // 이름으로 검색 (선택)
    List<Recipe> findAllByNameContaining(String name);
}
