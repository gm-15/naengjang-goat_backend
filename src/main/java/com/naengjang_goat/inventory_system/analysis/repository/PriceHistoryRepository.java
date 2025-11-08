package com.naengjang_goat.inventory_system.analysis.repository;

import com.naengjang_goat.inventory_system.analysis.PriceHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface PriceHistoryRepository extends JpaRepository<PriceHistory, Long> {
    List<PriceHistory> findAllByRecordedAtBetween(LocalDate start, LocalDate end);
}
