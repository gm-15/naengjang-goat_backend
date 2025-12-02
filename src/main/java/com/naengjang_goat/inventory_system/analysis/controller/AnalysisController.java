package com.naengjang_goat.inventory_system.analysis.controller;

import com.naengjang_goat.inventory_system.analysis.domain.PriceHistory;
import com.naengjang_goat.inventory_system.analysis.service.PriceHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/analysis")
@RequiredArgsConstructor
public class AnalysisController {

    private final PriceHistoryService priceHistoryService;

    // 전체 가격 데이터
    @GetMapping("/price-history/all")
    public List<PriceHistory> getAll() {
        return priceHistoryService.getAll();
    }

    // 품목별 가격 히스토리
    @GetMapping("/price-history/{productName}")
    public List<PriceHistory> getByProduct(@PathVariable String productName) {
        return priceHistoryService.getByProduct(productName);
    }

    // 날짜별 가격 히스토리
    @GetMapping("/price-history/date/{date}")
    public List<PriceHistory> getByDate(@PathVariable String date) {
        return priceHistoryService.getByDate(LocalDate.parse(date));
    }
}
