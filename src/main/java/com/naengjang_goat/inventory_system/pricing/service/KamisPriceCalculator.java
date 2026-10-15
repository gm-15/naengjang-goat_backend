package com.naengjang_goat.inventory_system.pricing.service;

import com.naengjang_goat.inventory_system.pricing.dto.KamisPriceDto;
import com.naengjang_goat.inventory_system.pricing.dto.TrendDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 상세/목록/추이가 동일한 관측 계열과 원/kg 단가를 사용한다. */
@Service
@RequiredArgsConstructor
public class KamisPriceCalculator {
    private final MarketPriceSeries series;

    public KamisPriceDto buildKamis(Long ingredientId) {
        var points = series.load(ingredientId, 30);
        if (points.isEmpty()) return null;
        var latest = points.getLast();
        var row = latest.observation();
        return KamisPriceDto.builder().currentPricePerKg(latest.price()).priceDate(latest.date())
                .weekAvg(MarketPriceSeries.average(points, latest.date(), 7))
                .monthAvg(MarketPriceSeries.average(points, latest.date(), 30))
                .priceBasis(row.getPriceBasis()).priceSource(row.getSource())
                .kindName(row.getKindName()).grade(row.getGrade()).unit("kg").build();
    }

    public Double dropRatePct(Long weekAvg, Long todayPrice) {
        return weekAvg == null || weekAvg <= 0 || todayPrice == null
                ? null : (weekAvg - todayPrice) * 100.0 / weekAvg;
    }

    public TrendDto buildTrend(Long ingredientId) {
        var points = series.load(ingredientId, 30);
        if (points.isEmpty()) return null;
        var latest = points.getLast();
        var window = points.stream().filter(p -> !p.date().isBefore(latest.date().minusDays(29))).toList();
        return TrendDto.builder().high(window.stream().mapToLong(MarketPriceSeries.Point::price).max().orElseThrow())
                .low(window.stream().mapToLong(MarketPriceSeries.Point::price).min().orElseThrow())
                .current(latest.price()).windowDays(window.size()).build();
    }

    Long toPricePerKg(String rawPrice, String unit) { return MarketPriceSeries.perKg(rawPrice, unit); }
}
