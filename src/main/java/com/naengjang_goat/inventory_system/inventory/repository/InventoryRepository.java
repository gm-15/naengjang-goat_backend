package com.naengjang_goat.inventory_system.inventory.repository;

import com.naengjang_goat.inventory_system.inventory.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    // raw_material_id 기준 1:1 매핑
    Optional<Inventory> findByRawMaterialId(Long rawMaterialId);
}
