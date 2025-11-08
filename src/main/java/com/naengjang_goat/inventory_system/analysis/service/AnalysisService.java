package com.naengjang_goat.inventory_system.analysis.service;

import com.naengjang_goat.inventory_system.analysis.domain.PriceHistory;
import com.naengjang_goat.inventory_system.analysis.repository.PriceHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
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
