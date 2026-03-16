package com.naengjang_goat.inventory_system.analysis.service;

import com.naengjang_goat.inventory_system.analysis.domain.PriceHistory;
import com.naengjang_goat.inventory_system.analysis.repository.PriceHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * [v2.1 비활성화]
 * 비활성화 사유: MarketPriceService로 대체
 * 재활성화 조건: v2.1 신규 서비스/컨트롤러 참고
 * 비활성화 일자: 2026-03-15
 */
// @Service  // [v2.1 비활성화]
@RequiredArgsConstructor
public class AnalysisService {

    private final PriceHistoryRepository priceHistoryRepository;

    public List<PriceHistory> getRecentPrices(Long rawMaterialId, int days) {
        LocalDate startDate = LocalDate.now().minusDays(days);
        return priceHistoryRepository.findAll().stream()
                .filter(p -> p.getRawMaterial().getId().equals(rawMaterialId)
                        && !p.getPriceDate().isBefore(startDate))
                .toList();
    }
}
