package com.naengjang_goat.inventory_system.analysis.repository;

import com.naengjang_goat.inventory_system.analysis.domain.PriceHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface PriceHistoryRepository extends JpaRepository<PriceHistory, Long> {

    // 특정 재료의 최근 N일 기록 (예: 최근 30일 가격 분석)
    List<PriceHistory> findTop30ByRawMaterialIdOrderByPriceDateDesc(Long rawMaterialId);

    // 특정 기간 동안의 가격 이력
    List<PriceHistory> findAllByRawMaterialIdAndPriceDateBetween(
            Long rawMaterialId,
            LocalDate start,
            LocalDate end
    );
}
