package com.naengjang_goat.inventory_system.inventory.repository;

import com.naengjang_goat.inventory_system.inventory.SaleHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface SaleHistoryRepository extends JpaRepository<SaleHistory, Long> {

    // 점주별 + 기간별 매출 내역 조회
    List<SaleHistory> findAllByUserIdAndSaleTimestampBetween(
            Long userId,
            LocalDateTime start,
            LocalDateTime end
    );
}
