package com.naengjang_goat.inventory_system.analysis.repository;

import com.naengjang_goat.inventory_system.analysis.domain.PriceHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface PriceHistoryRepository extends JpaRepository<PriceHistory, Long> {

    // 기존 코드 유지 --------------------------

    // 특정 재료의 최근 30일 기록
    List<PriceHistory> findTop30ByRawMaterialIdOrderByPriceDateDesc(Long rawMaterialId);

    // 특정 기간 동안의 가격 이력
    List<PriceHistory> findAllByRawMaterialIdAndPriceDateBetween(
            Long rawMaterialId,
            LocalDate start,
            LocalDate end
    );

    // 신규 추가 -------------------------------

    // 전체 가격 데이터를 날짜 최신순으로
    List<PriceHistory> findAllByOrderByPriceDateDesc();

    // 품목명 기준 조회 (오래된 날짜부터 오름차순)
    List<PriceHistory> findByProductNameOrderByPriceDateAsc(String productName);

    // 특정 날짜의 모든 가격 정보
    List<PriceHistory> findByPriceDate(LocalDate date);
}
