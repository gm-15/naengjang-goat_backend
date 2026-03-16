package com.naengjang_goat.inventory_system.analysis.controller;

import com.naengjang_goat.inventory_system.analysis.domain.PriceHistory;
import com.naengjang_goat.inventory_system.analysis.service.PriceHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * [v2.1 비활성화]
 * 비활성화 사유: MarketPriceController로 대체
 * 재활성화 조건: v2.1 신규 서비스/컨트롤러 참고
 * 비활성화 일자: 2026-03-15
 */
// @RestController  // [v2.1 비활성화]
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
