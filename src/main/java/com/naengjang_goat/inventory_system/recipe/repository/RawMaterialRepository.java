package com.naengjang_goat.inventory_system.recipe.repository;

import com.naengjang_goat.inventory_system.recipe.RawMaterial;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RawMaterialRepository extends JpaRepository<RawMaterial, Long> {
    Optional<RawMaterial> findByName(String name);
}
