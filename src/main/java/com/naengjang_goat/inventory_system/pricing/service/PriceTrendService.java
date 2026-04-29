package com.naengjang_goat.inventory_system.pricing.service;

import com.naengjang_goat.inventory_system.analysis.domain.MarketPrice;
import com.naengjang_goat.inventory_system.analysis.repository.MarketPriceRepository;
import com.naengjang_goat.inventory_system.inventory.domain.Ingredient;
import com.naengjang_goat.inventory_system.inventory.repository.IngredientRepository;
import com.naengjang_goat.inventory_system.pricing.domain.KamisCategory;
import com.naengjang_goat.inventory_system.pricing.dto.PriceTrendResponse;
import com.naengjang_goat.inventory_system.pricing.dto.TrendPointDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * UC-CORE-3 Part A — 가격 추이 시계열 + buySignal 계산.
 *
 * 알고리즘:
 *  - 데이터 최대 (days + 29)일 로딩 → 앞 29일은 첫 포인트의 30일 윈도우 확보용
 *  - 반환 포인트: 최근 days 일치
 *  - 가격 기준: wholesalePrice (null이면 retailPrice 폴백)
 *  - buySignal: currentPrice < monthAvg × (1 - category.buySignalThreshold)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PriceTrendService {

    private final MarketPriceRepository marketPriceRepository;
    private final IngredientRepository ingredientRepository;

    private static final int WEEK_WINDOW = 7;
    private static final int MONTH_WINDOW = 30;

    public PriceTrendResponse getTrend(Long ingredientId, int days) {
        // 1. 재료 조회
        Ingredient ingredient = ingredientRepository.findById(ingredientId)
                .orElseThrow(() -> new NoSuchElementException("재료 없음: " + ingredientId));

        // 2. 카테고리 → threshold
        KamisCategory category = resolveCategory(ingredient.getKamisCategory());
        double threshold = category.buySignalThreshold;

        // 3. 데이터 로딩 (days + 29일: 첫 포인트의 30일 윈도우 확보)
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays((long) days + 29);
        List<MarketPrice> raw = marketPriceRepository
                .findAllByIngredientIdAndReportedDateBetween(ingredientId, start, end);

        if (raw.isEmpty()) {
            log.warn("[PriceTrendService] 데이터 없음 ingredientId={}", ingredientId);
            return PriceTrendResponse.builder()
                    .ingredientId(ingredientId)
                    .ingredientName(ingredient.getName())
                    .currentBuySignal(false)
                    .signalReason("데이터 없음")
                    .dataCoverage(0)
                    .points(List.of())
                    .build();
        }

        // 4. 날짜 오름차순 정렬
        List<MarketPrice> sorted = raw.stream()
                .sorted(Comparator.comparing(MarketPrice::getReportedDate))
                .collect(Collectors.toList());

        // 5. 가격 시리즈 (wholesalePrice 우선, null이면 retailPrice)
        List<Long> priceSeries = sorted.stream()
                .map(mp -> resolvePrice(mp))
                .collect(Collectors.toList());

        // 6. 최근 days 포인트만 반환
        int totalSize = sorted.size();
        int startIdx = Math.max(0, totalSize - days);

        List<TrendPointDto> points = new ArrayList<>();
        for (int i = startIdx; i < totalSize; i++) {
            MarketPrice mp = sorted.get(i);
            Long wholesale = parsePrice(mp.getWholesalePrice());
            Long retail = parsePrice(mp.getRetailPrice());
            Long current = wholesale != null ? wholesale : retail;

            Long weekAvg = slidingAverage(priceSeries, i, WEEK_WINDOW);
            Long monthAvg = slidingAverage(priceSeries, i, MONTH_WINDOW);

            boolean buySignal = false;
            if (current != null && monthAvg != null && monthAvg > 0) {
                buySignal = current < monthAvg * (1 - threshold);
            }

            points.add(new TrendPointDto(
                    mp.getReportedDate(),
                    wholesale,
                    retail,
                    weekAvg,
                    monthAvg,
                    buySignal
            ));
        }

        // 7. 현재 신호 요약
        TrendPointDto last = points.isEmpty() ? null : points.get(points.size() - 1);
        boolean currentBuySignal = last != null && last.buySignal();
        String signalReason = buildSignalReason(last, threshold);

        return PriceTrendResponse.builder()
                .ingredientId(ingredientId)
                .ingredientName(ingredient.getName())
                .currentBuySignal(currentBuySignal)
                .signalReason(signalReason)
                .dataCoverage(points.size())
                .points(points)
                .build();
    }

    // ─── private helpers ────────────────────────────────────────────────────

    private Long resolvePrice(MarketPrice mp) {
        Long w = parsePrice(mp.getWholesalePrice());
        return w != null ? w : parsePrice(mp.getRetailPrice());
    }

    /**
     * 인덱스 endIdx 기준 직전 windowSize 개 평균 (null 값 제외).
     */
    private Long slidingAverage(List<Long> series, int endIdx, int windowSize) {
        int from = Math.max(0, endIdx - windowSize + 1);
        List<Long> window = series.subList(from, endIdx + 1).stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        if (window.isEmpty()) return null;
        long sum = window.stream().mapToLong(Long::longValue).sum();
        return Math.round((double) sum / window.size());
    }

    private KamisCategory resolveCategory(String name) {
        if (name == null || name.isBlank()) return KamisCategory.VEGETABLES;
        try {
            return KamisCategory.valueOf(name);
        } catch (IllegalArgumentException e) {
            return KamisCategory.VEGETABLES;
        }
    }

    private String buildSignalReason(TrendPointDto last, double threshold) {
        if (last == null) return "데이터 없음";
        Long current = last.wholesalePrice() != null ? last.wholesalePrice() : last.retailPrice();
        if (current == null) return "가격 데이터 없음";
        if (last.monthAvg() == null) return "30일 평균 데이터 부족";

        double discountPct = (last.monthAvg() - current) * 100.0 / last.monthAvg();
        String priceLabel = last.wholesalePrice() != null ? "도매가" : "소매가";

        if (last.buySignal()) {
            return String.format("현재 %s(%,d원)가 30일 평균(%,d원)보다 %.1f%% 저렴",
                    priceLabel, current, last.monthAvg(), discountPct);
        } else if (discountPct > 0) {
            return String.format("현재 %s(%,d원)가 30일 평균(%,d원)보다 %.1f%% 저렴 (임계값 %.0f%% 미달)",
                    priceLabel, current, last.monthAvg(), discountPct, threshold * 100);
        } else {
            return String.format("현재 %s(%,d원)가 30일 평균(%,d원)보다 %.1f%% 높음",
                    priceLabel, current, last.monthAvg(), Math.abs(discountPct));
        }
    }

    /** "1,234" → 1234. 파싱 실패 또는 빈 값 → null. */
    private Long parsePrice(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return Long.parseLong(raw.replaceAll(",", "").trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
