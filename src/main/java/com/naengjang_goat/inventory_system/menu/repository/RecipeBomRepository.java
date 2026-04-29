package com.naengjang_goat.inventory_system.menu.repository;

import com.naengjang_goat.inventory_system.menu.domain.RecipeBom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RecipeBomRepository extends JpaRepository<RecipeBom, Long> {

    // 메뉴별 BOM 조회
    List<RecipeBom> findAllByMenuId(Long menuId);

    /**
     * 특정 점주의 특정 재료에 대한 BOM 전체 조회.
     * DailySalesService — "이 재료를 사용하는 메뉴 목록과 소요량" 계산용.
     */
    @Query("SELECT b FROM RecipeBom b " +
           "JOIN FETCH b.menu m " +
           "WHERE b.ingredient.id = :ingredientId AND m.user.id = :userId")
    List<RecipeBom> findAllByIngredientIdAndUserId(
            @Param("ingredientId") Long ingredientId,
            @Param("userId") Long userId);
}
