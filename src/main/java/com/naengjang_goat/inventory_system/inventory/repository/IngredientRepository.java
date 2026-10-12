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

    // sim, 2026-10-12 — KAMIS 배치가 모든 user 에게 가격을 저장하도록 이름/코드 기반 전체 조회 추가.
    //   findBy* 는 Optional 1개만 반환하므로 다수 user Ingredient 중 비결정적으로 하나만 저장됨.
    List<Ingredient> findAllByName(String name);
    List<Ingredient> findAllByKamisItemCode(String kamisItemCode);

    // EKAPE 연동: LIVESTOCK 카테고리 재료 전체 조회
    List<Ingredient> findByKamisCategory(String kamisCategory);

    // KAMIS 배치: item_code 기반 매칭 (이름 변형 대응)
    Optional<Ingredient> findByKamisItemCode(String kamisItemCode);
}
