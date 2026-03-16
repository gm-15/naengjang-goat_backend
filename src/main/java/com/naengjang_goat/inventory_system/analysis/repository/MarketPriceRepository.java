package com.naengjang_goat.inventory_system.analysis.repository;

import com.naengjang_goat.inventory_system.analysis.domain.MarketPrice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface MarketPriceRepository extends JpaRepository<MarketPrice, Long> {

    // 재료별 최근 N개 시세 (내림차순)
    List<MarketPrice> findTop30ByIngredientIdOrderByReportedDateDesc(Long ingredientId);

    // 재료별 기간 조회
    List<MarketPrice> findAllByIngredientIdAndReportedDateBetween(
            Long ingredientId, LocalDate start, LocalDate end);

    // 특정 날짜 전체 시세
    List<MarketPrice> findByReportedDate(LocalDate date);

    // 최신순 전체 조회
    List<MarketPrice> findAllByOrderByReportedDateDesc();
}
