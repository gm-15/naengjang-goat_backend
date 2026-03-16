package com.naengjang_goat.inventory_system.inventory.repository;

import com.naengjang_goat.inventory_system.inventory.domain.RawMaterial;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

import java.util.List;
import java.util.Optional;

/**
 * [v2.1 비활성화]
 * 비활성화 사유: IngredientRepository로 대체
 * 비활성화 일자: 2026-03-15
 */
@NoRepositoryBean  // [v2.1 비활성화] — Spring Data 빈 생성 차단
public interface RawMaterialRepository extends JpaRepository<RawMaterial, Long> {

    // 점주별 원재료 목록
    List<RawMaterial> findAllByUserId(Long userId);

    // 점주 기준 + 이름 중복 체크/조회
    Optional<RawMaterial> findByUserIdAndName(Long userId, String name);

    Optional<RawMaterial> findByName(String name);

}
