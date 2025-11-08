package com.naengjang_goat.inventory_system.inventory.repository;

import com.naengjang_goat.inventory_system.inventory.domain.RawMaterial;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RawMaterialRepository extends JpaRepository<RawMaterial, Long> {

    // 점주별 원재료 목록
    List<RawMaterial> findAllByUserId(Long userId);

    // 점주 기준 + 이름 중복 체크/조회
    Optional<RawMaterial> findByUserIdAndName(Long userId, String name);
}
