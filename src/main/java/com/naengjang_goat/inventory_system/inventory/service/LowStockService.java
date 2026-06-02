package com.naengjang_goat.inventory_system.inventory.service;

import com.naengjang_goat.inventory_system.inventory.domain.Ingredient;
import com.naengjang_goat.inventory_system.inventory.dto.DepletionResult;
import com.naengjang_goat.inventory_system.inventory.dto.LowStockItemDto;
import com.naengjang_goat.inventory_system.inventory.repository.IngredientRepository;
import com.naengjang_goat.inventory_system.purchase.repository.PurchaseOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * UC-CORE-1 — 재고 부족 Top N 서비스 (v2, 2026-04-29).
 *
 * 변경 이력:
 *   v1: stockRatio = currentStock / warningThreshold
 *   v2: stockRatio = currentStock / (nextOrderDayDistance × dailyAvgSales)
 *       → 다음 발주일까지 버틸 수 있는지 실제 판매 속도 기반 예측
 *   v2.1 (sim, 2026-06-01):
 *       응답에 시안 친화 필드 추가 — category·image·warningThreshold·
 *       simpleStockRatio (= currentStock / warningThreshold, v1 의미)·lastOrderedAt.
 *       v2 stockRatio 와 v1 의미 simpleStockRatio 를 둘 다 노출해 프론트가
 *       시안 카드 "재고율 10%" 라벨을 자연스럽게 채울 수 있게 함.
 *
 * 로직:
 *   1. 점주 재료 전체 조회 (한 번에 fetch)
 *   2. 각 재료별 DepletionCalculatorService.calculate() 호출
 *   3. stockRatio 오름차순 정렬 (낮을수록 위험)
 *      - dailyAvgSales == 0 재료 → Double.MAX_VALUE → 최후순위
 *   4. 상위 limit 건 → Ingredient 캐시 + 마지막 발주일자 조회로 응답 보강
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
    private final PurchaseOrderRepository purchaseOrderRepository;

    @Transactional(readOnly = true)
    public List<LowStockItemDto> getTopLowStock(Long userId, int limit) {

        // 1. 점주 재료 전체 조회 + id 키 캐시 (시안 필드 보강용)
        List<Ingredient> allIngredients = ingredientRepository.findAllByUserIdWithFetch(userId);
        if (allIngredients.isEmpty()) {
            return List.of();
        }
        Map<Long, Ingredient> byId = allIngredients.stream()
                .collect(Collectors.toMap(Ingredient::getId, Function.identity()));

        // 2. 발주일 거리 1회 계산 (동일 userId 이므로 루프 밖에서 처리 — store_settings N+1 방지)
        int nextOrderDayDistance = depletionCalculatorService.calcNextOrderDayDistance(userId);

        // 3. 각 재료 소진 계산 + 4. 정렬 + 5. limit
        return byId.keySet().stream()
                .map(id -> {
                    try {
                        DepletionResult result = depletionCalculatorService.calculate(userId, id, nextOrderDayDistance);
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
                // sim, 2026-06-01 — TOP N 만 시안 필드 보강 (전체 ingredient 에 안 함 — 쿼리 절약)
                .map(dto -> enrichForView(userId, dto, byId))
                .collect(Collectors.toList());
    }

    /**
     * 시안 카드 매핑용 추가 필드 보강.
     * sim, 2026-06-01.
     */
    private LowStockItemDto enrichForView(Long userId,
                                          LowStockItemDto base,
                                          Map<Long, Ingredient> byId) {
        Ingredient ing = byId.get(base.getIngredientId());
        BigDecimal warningThreshold = ing != null ? ing.getWarningThreshold() : null;
        LocalDate lastOrderedAt = purchaseOrderRepository
                .findLastOrderedAtByUserIdAndIngredientId(userId, base.getIngredientId());

        return base.toBuilder()
                .category(ing != null ? ing.getCategory() : null)
                .image(ing != null ? ing.getImageUrl() : null)
                .warningThreshold(warningThreshold)
                .simpleStockRatio(calcSimpleRatio(base.getCurrentStock(), warningThreshold))
                .lastOrderedAt(lastOrderedAt)
                .build();
    }

    /**
     * simpleStockRatio = currentStock / warningThreshold.
     * warningThreshold 가 null·0 이면 null 반환.
     */
    private Double calcSimpleRatio(BigDecimal currentStock, BigDecimal warningThreshold) {
        if (currentStock == null || warningThreshold == null
                || warningThreshold.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        return currentStock
                .divide(warningThreshold, 4, RoundingMode.HALF_UP)
                .doubleValue();
    }
}
