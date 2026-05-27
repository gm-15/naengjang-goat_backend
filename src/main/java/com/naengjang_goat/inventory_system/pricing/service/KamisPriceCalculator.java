package com.naengjang_goat.inventory_system.pricing.service;

import com.naengjang_goat.inventory_system.analysis.domain.MarketPrice;
import com.naengjang_goat.inventory_system.analysis.repository.MarketPriceRepository;
import com.naengjang_goat.inventory_system.pricing.dto.KamisPriceDto;
import com.naengjang_goat.inventory_system.pricing.dto.TrendDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.OptionalDouble;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * KAMIS MarketPrice 데이터 → 주/월 평균, 하락률, 추이 계산.
 *
 * 주의:
 *  - MarketPrice.retailPrice 는 "1,234" 형식 String → 콤마 제거 후 long
 *  - 모든 가격은 원/kg 으로 정규화해서 반환 (단위 혼재 방지)
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
     * 모든 가격은 원/kg 으로 정규화.
     */
    public KamisPriceDto buildKamis(Long ingredientId) {
        List<MarketPrice> recent = marketPriceRepository
                .findTop30ByIngredientIdOrderByReportedDateDesc(ingredientId);
        if (recent.isEmpty()) {
            return null;
        }

        MarketPrice latest = recent.get(0);
        Long todayPrice = toPricePerKg(latest.getRetailPrice(), latest.getUnit());
        if (todayPrice == null) {
            return null;
        }

        Long weekAvg = averagePerKg(recent, WEEK_DAYS);
        Long monthAvg = averagePerKg(recent, MONTH_DAYS);

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

    /** 30일 추이 (high/current/low). 모든 가격 원/kg 정규화. 데이터 없으면 null. */
    public TrendDto buildTrend(Long ingredientId) {
        List<MarketPrice> recent = marketPriceRepository
                .findTop30ByIngredientIdOrderByReportedDateDesc(ingredientId);
        if (recent.isEmpty()) {
            return null;
        }

        long high = Long.MIN_VALUE;
        long low = Long.MAX_VALUE;
        Long current = toPricePerKg(recent.get(0).getRetailPrice(), recent.get(0).getUnit());
        int counted = 0;

        for (MarketPrice mp : recent) {
            Long p = toPricePerKg(mp.getRetailPrice(), mp.getUnit());
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

    /** 원/kg 기준 평균. */
    private Long averagePerKg(List<MarketPrice> records, int maxCount) {
        OptionalDouble avg = records.stream()
                .limit(maxCount)
                .map(mp -> toPricePerKg(mp.getRetailPrice(), mp.getUnit()))
                .filter(p -> p != null)
                .mapToLong(Long::longValue)
                .average();
        return avg.isPresent() ? Math.round(avg.getAsDouble()) : null;
    }

    /**
     * 원/kg 단가 반환.
     * 예: price="18,500", unit="20kg" → 925
     *     price="5,500",  unit="1kg"  → 5500
     *     unit 파싱 불가 시 원본 가격 그대로 반환.
     */
    Long toPricePerKg(String rawPrice, String unit) {
        Long price = parsePrice(rawPrice);
        if (price == null) return null;
        double kg = extractKg(unit);
        return kg > 0 ? Math.round(price / kg) : price;
    }

    /** "20kg" → 20.0, "500g" → 0.5, 파싱 불가 → 1.0 */
    private double extractKg(String unit) {
        if (unit == null || unit.isBlank()) return 1.0;
        String u = unit.toLowerCase().trim();

        Matcher kgMatcher = Pattern.compile("([\\d.]+)\\s*kg").matcher(u);
        if (kgMatcher.find()) return Double.parseDouble(kgMatcher.group(1));

        Matcher gMatcher = Pattern.compile("([\\d.]+)\\s*g\\b").matcher(u);
        if (gMatcher.find()) return Double.parseDouble(gMatcher.group(1)) / 1000.0;

        return 1.0;
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
