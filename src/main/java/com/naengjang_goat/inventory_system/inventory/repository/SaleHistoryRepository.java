package com.naengjang_goat.inventory_system.inventory.repository;

import com.naengjang_goat.inventory_system.inventory.SaleHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface SaleHistoryRepository extends JpaRepository<SaleHistory, Long> {
    List<SaleHistory> findAllBySoldAtBetween(LocalDateTime start, LocalDateTime end);
}
