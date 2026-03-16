package com.naengjang_goat.inventory_system.menu.repository;

import com.naengjang_goat.inventory_system.menu.domain.RecipeBom;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RecipeBomRepository extends JpaRepository<RecipeBom, Long> {

    // 메뉴별 BOM 조회
    List<RecipeBom> findAllByMenuId(Long menuId);
}
