package com.naengjang_goat.inventory_system.inventory.repository;

import com.naengjang_goat.inventory_system.inventory.domain.SaleItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

/**
 * [v2.1 비활성화]
 * 비활성화 사유: OrderItemRepository로 대체
 * 비활성화 일자: 2026-03-15
 */
@NoRepositoryBean
public interface SaleItemRepository extends JpaRepository<SaleItem, Long> {
}
