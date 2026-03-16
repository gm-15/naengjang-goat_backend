package com.naengjang_goat.inventory_system.inventory.repository;

import com.naengjang_goat.inventory_system.inventory.domain.SaleHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

import java.time.LocalDateTime;
import java.util.List;

/**
 * [v2.1 비활성화]
 * 비활성화 사유: OrderRepository로 대체
 * 비활성화 일자: 2026-03-15
 */
@NoRepositoryBean  // [v2.1 비활성화]
public interface SaleHistoryRepository extends JpaRepository<SaleHistory, Long> {

    // 점주별 + 기간별 매출 내역 조회
    List<SaleHistory> findAllByUserIdAndSaleTimestampBetween(
            Long userId,
            LocalDateTime start,
            LocalDateTime end
    );
}
