package com.naengjang_goat.inventory_system.inventory.repository;

import com.naengjang_goat.inventory_system.inventory.domain.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

import java.util.Optional;

/**
 * [v2.1 비활성화]
 * 비활성화 사유: InventoryBatchRepository로 대체
 * 비활성화 일자: 2026-03-15
 */
@NoRepositoryBean  // [v2.1 비활성화]
public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    // raw_material_id 기준 1:1 매핑
    Optional<Inventory> findByRawMaterialId(Long rawMaterialId);
    Optional<Inventory> findByRawMaterialName(String name);

}
