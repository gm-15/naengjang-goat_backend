package com.naengjang_goat.inventory_system.menu.repository;

import com.naengjang_goat.inventory_system.menu.domain.Menu;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MenuRepository extends JpaRepository<Menu, Long> {

    // 점주별 메뉴 + BOM 한번에 조회 (N+1 방지)
    @Query("SELECT m FROM Menu m JOIN FETCH m.bom b JOIN FETCH b.ingredient WHERE m.user.id = :userId")
    List<Menu> findAllByUserIdWithBom(@Param("userId") Long userId);

    // 단건 조회 + BOM fetch join (트랜잭션 없는 컨텍스트에서 LazyInit 방지)
    @Query("SELECT m FROM Menu m LEFT JOIN FETCH m.bom b LEFT JOIN FETCH b.ingredient WHERE m.id = :id")
    Optional<Menu> findByIdWithBom(@Param("id") Long id);

    // 이름 검색
    List<Menu> findAllByUserIdAndNameContaining(Long userId, String name);
}
