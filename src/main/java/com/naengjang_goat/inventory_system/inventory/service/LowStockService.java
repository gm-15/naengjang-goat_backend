package com.naengjang_goat.inventory_system.inventory.service;

import com.naengjang_goat.inventory_system.inventory.dto.DepletionResult;
import com.naengjang_goat.inventory_system.inventory.dto.LowStockItemDto;
import com.naengjang_goat.inventory_system.inventory.repository.IngredientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * UC-CORE-1 — 재고 부족 Top N 서비스 (v2, 2026-04-29).
 *
 * 변경 이력:
 *   v1: stockRatio = currentStock / warningThreshold
 *   v2: stockRatio = currentStock / (nextOrderDayDistance × dailyAvgSales)
 *       → 다음 발주일까지 버틸 수 있는지 실제 판매 속도 기반 예측
 *
 * 로직:
 *   1. 점주 재료 목록 조회
 *   2. 각 재료별 DepletionCalculatorService.calculate() 호출
 *      → stockRatio, grade, estimatedDepletionDate, orderAlert 계산
 *   3. stockRatio 오름차순 정렬 (낮을수록 위험)
 *      - dailyAvgSales == 0 재료 → Double.MAX_VALUE → 최후순위
 *   4. 상위 limit 건 반환
 *
 * 정렬 기준:
 *   - stockRatio 오름차순 (DANGER 먼저)
 *   - 동률 시 ingredientName 오름차순
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LowStockService {

    private final IngredientRepository ingredientRepository;
    private final DepletionCalculatorService depletionCalculatorService;

    @Transactional(readOnly = true)
    public List<LowStockItemDto> getTopLowStock(Long userId, int limit) {

        // 1. 점주 재료 ID 목록
        List<Long> ingredientIds = ingredientRepository.findAllByUserIdWithFetch(userId)
                .stream()
                .map(i -> i.getId())
                .collect(Collectors.toList());

        if (ingredientIds.isEmpty()) {
            return List.of();
        }

        // 2. 각 재료 소진 계산 + 3. 정렬 + 4. limit
        return ingredientIds.stream()
                .map(id -> {
                    try {
                        DepletionResult result = depletionCalculatorService.calculate(userId, id);
                        return LowStockItemDto.from(result);
                    } catch (Exception e) {
                        log.warn("[LowStockService] 소진 계산 실패 ingredientId={}: {}", id, e.getMessage());
                        return null;
                    }
                })
                .filter(dto -> dto != null)
                .sorted(Comparator
                        .comparingDouble(LowStockItemDto::getStockRatio)
                        .thenComparing(LowStockItemDto::getIngredientName))
                .limit(limit)
                .collect(Collectors.toList());
    }
}
