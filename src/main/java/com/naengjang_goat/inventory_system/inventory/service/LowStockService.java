package com.naengjang_goat.inventory_system.inventory.service;

import com.naengjang_goat.inventory_system.inventory.domain.Ingredient;
import com.naengjang_goat.inventory_system.inventory.dto.LowStockItemDto;
import com.naengjang_goat.inventory_system.inventory.repository.IngredientRepository;
import com.naengjang_goat.inventory_system.inventory.repository.InventoryBatchRepository;
import com.naengjang_goat.inventory_system.inventory.repository.projection.IngredientStockSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * UC-CORE-1 — 재고 부족 Top N 서비스.
 *
 * 로직:
 *   1. 점주의 모든 재료 목록 조회 (Ingredient + user Fetch Join)
 *   2. 점주 배치 재고 집계 (ingredientId → totalQuantity, 단 1회 쿼리)
 *   3. stockRatio = totalQuantity / warningThreshold 계산
 *   4. stockRatio 오름차순 정렬 → 상위 limit 건 반환
 *
 * 정렬 기준:
 *   - warningThreshold 설정 재료 먼저, 미설정 재료는 최후순위
 *   - 동일 ratio면 재료명 오름차순
 */
@Service
@RequiredArgsConstructor
public class LowStockService {

    private final IngredientRepository ingredientRepository;
    private final InventoryBatchRepository batchRepository;

    @Transactional(readOnly = true)
    public List<LowStockItemDto> getTopLowStock(Long userId, int limit) {

        // 1. 점주 재료 목록 (N+1 방지 Fetch Join)
        List<Ingredient> ingredients = ingredientRepository.findAllByUserIdWithFetch(userId);
        if (ingredients.isEmpty()) {
            return List.of();
        }

        // 2. 배치 재고 집계 (단 1회 쿼리) → Map<ingredientId, totalQuantity>
        Map<Long, BigDecimal> stockMap = batchRepository.findStockSummaryByUserId(userId)
                .stream()
                .collect(Collectors.toMap(
                        IngredientStockSummary::getIngredientId,
                        IngredientStockSummary::getTotalQuantity
                ));

        // 3. DTO 변환 + 4. 정렬 + 상위 limit 추출
        return ingredients.stream()
                .map(ingredient -> {
                    BigDecimal current = stockMap.getOrDefault(
                            ingredient.getId(), BigDecimal.ZERO);
                    return LowStockItemDto.of(ingredient, current);
                })
                .sorted(Comparator
                        .comparingDouble(LowStockItemDto::getStockRatio)
                        .thenComparing(LowStockItemDto::getIngredientName))
                .limit(limit)
                .collect(Collectors.toList());
    }
}
