package com.naengjang_goat.inventory_system.analysis.service;

import com.naengjang_goat.inventory_system.analysis.domain.PriceHistory;
import com.naengjang_goat.inventory_system.analysis.repository.PriceHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PriceHistoryService {

    private final PriceHistoryRepository priceHistoryRepository;

    // 전체 시세 조회
    public List<PriceHistory> getAll() {
        return priceHistoryRepository.findAllByOrderByPriceDateDesc();
    }

    // 특정 품목 시세 조회
    public List<PriceHistory> getByProduct(String productName) {
        return priceHistoryRepository.findByProductNameOrderByPriceDateAsc(productName);
    }

    // 특정 날짜 데이터 조회
    public List<PriceHistory> getByDate(LocalDate date) {
        return priceHistoryRepository.findByPriceDate(date);
    }
}
