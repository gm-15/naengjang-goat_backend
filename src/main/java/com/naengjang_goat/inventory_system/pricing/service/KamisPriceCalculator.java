package com.naengjang_goat.inventory_system.pricing.service;

import com.naengjang_goat.inventory_system.analysis.domain.MarketPrice;
import com.naengjang_goat.inventory_system.analysis.repository.MarketPriceRepository;
import com.naengjang_goat.inventory_system.pricing.dto.KamisPriceDto;
import com.naengjang_goat.inventory_system.pricing.dto.TrendDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.OptionalDouble;

/**
 * KAMIS MarketPrice 데이터 → 주/월 평균, 하락률, 추이 계산.
 *
 * 주의:
 *  - MarketPrice.retailPrice 는 "1,234" 형식 String → 콤마 제거 후 long
 *  - 가격 단위가 "원/kg" 가 아닐 수도 있음 (KAMIS 원본 단위 추적 필요) — Demo 는 retailPrice 그대로 사용
 *  - KAMIS 배치가 비활성화 상태면 DB 가 비어있을 수 있음 → 모든 메서드가 null/빈 결과 안전 처리
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KamisPriceCalculator {

    private static final int WEEK_DAYS = 7;
    private static final int MONTH_DAYS = 30;

    private final MarketPriceRepository marketPriceRepository;

    /**
     * 해당 ingredient 의 KamisPriceDto 빌드. 데이터 없으면 null 반환.
     */
    public KamisPriceDto buildKamis(Long ingredientId) {
        List<MarketPrice> recent = marketPriceRepository
                .findTop30ByIngredientIdOrderByReportedDateDesc(ingredientId);
        if (recent.isEmpty()) {
            return null;
        }

        MarketPrice latest = recent.get(0);
        Long todayPrice = parsePrice(latest.getRetailPrice());
        if (todayPrice == null) {
            return null;
        }

        Long weekAvg = average(recent, WEEK_DAYS);
        Long monthAvg = average(recent, MONTH_DAYS);

        return KamisPriceDto.builder()
                .currentPricePerKg(todayPrice)
                .priceDate(latest.getReportedDate())
                .weekAvg(weekAvg)
                .monthAvg(monthAvg)
                .build();
    }

    /** 하락률 = (weekAvg - todayPrice) / weekAvg * 100. weekAvg 0 이면 null. */
    public Double dropRatePct(Long weekAvg, Long todayPrice) {
        if (weekAvg == null || weekAvg <= 0 || todayPrice == null) {
            return null;
        }
        return (weekAvg - todayPrice) * 100.0 / weekAvg;
    }

    /** 30일 추이 (high/current/low). 데이터 없으면 null. */
    public TrendDto buildTrend(Long ingredientId) {
        List<MarketPrice> recent = marketPriceRepository
                .findTop30ByIngredientIdOrderByReportedDateDesc(ingredientId);
        if (recent.isEmpty()) {
            return null;
        }

        long high = Long.MIN_VALUE;
        long low = Long.MAX_VALUE;
        Long current = parsePrice(recent.get(0).getRetailPrice());
        int counted = 0;

        for (MarketPrice mp : recent) {
            Long p = parsePrice(mp.getRetailPrice());
            if (p == null) continue;
            if (p > high) high = p;
            if (p < low) low = p;
            counted++;
        }

        if (counted == 0) {
            return null;
        }

        return TrendDto.builder()
                .high(high)
                .current(current)
                .low(low)
                .windowDays(Math.min(counted, MONTH_DAYS))
                .build();
    }

    private Long average(List<MarketPrice> records, int maxCount) {
        OptionalDouble avg = records.stream()
                .limit(maxCount)
                .map(mp -> parsePrice(mp.getRetailPrice()))
                .filter(p -> p != null)
                .mapToLong(Long::longValue)
                .average();
        return avg.isPresent() ? Math.round(avg.getAsDouble()) : null;
    }

    /** "1,234" → 1234. 파싱 실패 null. */
    private Long parsePrice(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return Long.parseLong(raw.replaceAll(",", "").trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
