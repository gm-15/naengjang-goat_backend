package com.naengjang_goat.inventory_system.pricing.service;

import com.naengjang_goat.inventory_system.inventory.repository.IngredientRepository;
import com.naengjang_goat.inventory_system.pricing.domain.KamisCategory;
import com.naengjang_goat.inventory_system.pricing.dto.PriceTrendResponse;
import com.naengjang_goat.inventory_system.pricing.dto.TrendPointDto;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PriceTrendService {
    private final MarketPriceSeries series;
    private final IngredientRepository ingredients;
    private final Clock clock;

    public PriceTrendResponse getTrend(Long ingredientId, int days) {
        var ingredient = ingredients.findById(ingredientId)
                .orElseThrow(() -> new NoSuchElementException("재료 없음: " + ingredientId));
        var category = resolveCategory(ingredient.getKamisCategory());
        var history = series.load(ingredientId, days);
        LocalDate firstDay = LocalDate.now(clock).minusDays(days - 1L);
        var points = history.stream().filter(p -> !p.date().isBefore(firstDay)).map(p -> {
            Long week = MarketPriceSeries.average(history, p.date(), 7);
            Long month = MarketPriceSeries.average(history, p.date(), 30);
            boolean signal = month != null && month > 0 && p.price() < month * (1 - category.buySignalThreshold);
            return new TrendPointDto(p.date(), p.wholesale(), p.retail(), week, month, signal);
        }).toList();
        var last = points.isEmpty() ? null : points.getLast();
        var observation = history.isEmpty() ? null : history.getLast().observation();
        return PriceTrendResponse.builder().ingredientId(ingredientId).ingredientName(ingredient.getName())
                .currentBuySignal(last != null && last.buySignal())
                .signalReason(buildSignalReason(last, category.buySignalThreshold))
                .dataCoverage(points.size()).points(points)
                .priceBasis(observation == null ? null : observation.getPriceBasis())
                .priceSource(observation == null ? null : observation.getSource())
                .kindName(observation == null ? null : observation.getKindName())
                .grade(observation == null ? null : observation.getGrade()).unit("kg").build();
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

}
