package com.naengjang_goat.inventory_system.inventory.repository;

import com.naengjang_goat.inventory_system.inventory.domain.Ingredient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface IngredientRepository extends JpaRepository<Ingredient, Long> {

    // 점주별 전체 재료 조회 (Fetch Join으로 N+1 방지)
    @Query("SELECT i FROM Ingredient i JOIN FETCH i.user WHERE i.user.id = :userId")
    List<Ingredient> findAllByUserIdWithFetch(@Param("userId") Long userId);

    // 점주 + 이름 기준 중복 체크
    Optional<Ingredient> findByUserIdAndName(Long userId, String name);

    // 이름으로 단건 조회
    Optional<Ingredient> findByName(String name);

    /**
     * 동일 이름 ingredient 전체 user 조회.
     * KAMIS 시세를 모든 사용자 ingredient 에 매핑하기 위함.
     * sim, 2026-06-05 — kim 인수인계서 5-2 섹션 영구 해결.
     */
    List<Ingredient> findAllByName(String name);

    // EKAPE 연동: LIVESTOCK 카테고리 재료 전체 조회
    List<Ingredient> findByKamisCategory(String kamisCategory);

    // KAMIS 배치: item_code 기반 매칭 (이름 변형 대응)
    Optional<Ingredient> findByKamisItemCode(String kamisItemCode);
}
