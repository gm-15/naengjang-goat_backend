package com.naengjang_goat.inventory_system.pricing.service;

import com.naengjang_goat.inventory_system.inventory.domain.Ingredient;
import com.naengjang_goat.inventory_system.inventory.repository.IngredientRepository;
import com.naengjang_goat.inventory_system.pricing.domain.KamisCategory;
import com.naengjang_goat.inventory_system.pricing.dto.BuySignalTodayDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;

/**
 * 홈 모달용 — 사용자 소유 재료 중 buySignal 조건 충족 재료 중
 * 할인률(dropPercent) 가장 큰 1개 반환. 없으면 null.
 */
@Service
@RequiredArgsConstructor
public class BuySignalTodayService {

    private final IngredientRepository ingredientRepository;
    private final KamisPriceCalculator kamisPriceCalculator;

    @Transactional(readOnly = true)
    public BuySignalTodayDto findTop(Long userId) {
        return ingredientRepository.findAll().stream()
                .filter(i -> i.getUser() != null && userId.equals(i.getUser().getId()))
                .filter(i -> i.getKamisCategory() != null && !i.getKamisCategory().isBlank())
                .map(this::toSignal)
                .filter(dto -> dto != null)
                .max(Comparator.comparingDouble(BuySignalTodayDto::dropPercent))
                .orElse(null);
    }

    private BuySignalTodayDto toSignal(Ingredient i) {
        KamisCategory cat;
        try {
            cat = KamisCategory.valueOf(i.getKamisCategory());
        } catch (IllegalArgumentException e) {
            return null;
        }

        var k = kamisPriceCalculator.buildKamis(i.getId());
        if (k == null || k.getMonthAvg() == null || k.getCurrentPricePerKg() == null) return null;

        double drop = 1.0 - k.getCurrentPricePerKg().doubleValue() / k.getMonthAvg().doubleValue();
        if (drop < cat.buySignalThreshold) return null;

        return new BuySignalTodayDto(
                i.getId(),
                i.getName(),
                cat.name(),
                k.getMonthAvg(),
                k.getCurrentPricePerKg(),
                Math.round(drop * 1000.0) / 10.0
        );
    }
}
