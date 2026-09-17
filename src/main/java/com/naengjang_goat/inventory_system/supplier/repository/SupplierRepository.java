package com.naengjang_goat.inventory_system.supplier.repository;

import com.naengjang_goat.inventory_system.supplier.domain.Supplier;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * @author sim
 * @since 2026-06-04
 */
public interface SupplierRepository extends JpaRepository<Supplier, Long> {

    /** 점주별 거래처 전체 — 이름 가나다 정렬 */
    List<Supplier> findAllByUserIdOrderByNameAsc(Long userId);

    /** 점주 + 이름 unique check */
    Optional<Supplier> findByUserIdAndName(Long userId, String name);

    /** 점주별 발주 가능 여부 (시드 멱등 체크용) */
    boolean existsByUserId(Long userId);
}
