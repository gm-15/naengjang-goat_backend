package com.naengjang_goat.inventory_system.menu.repository;

import com.naengjang_goat.inventory_system.menu.domain.RecipeTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RecipeTemplateRepository extends JpaRepository<RecipeTemplate, Long> {

    /**
     * 카테고리 목록에 해당하는 템플릿 + BOM 한 번에 조회 (N+1 방지).
     * DISTINCT: JOIN FETCH로 인한 템플릿 row 중복 제거.
     */
    @Query("SELECT DISTINCT t FROM RecipeTemplate t LEFT JOIN FETCH t.bomList WHERE t.category IN :categories")
    List<RecipeTemplate> findAllByCategoryInWithBom(@Param("categories") List<String> categories);
}
