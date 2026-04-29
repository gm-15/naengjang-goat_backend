package com.naengjang_goat.inventory_system.inventory.service;

import com.naengjang_goat.inventory_system.global.util.UnitConverter;
import com.naengjang_goat.inventory_system.inventory.domain.Ingredient;
import com.naengjang_goat.inventory_system.inventory.repository.IngredientRepository;
import com.naengjang_goat.inventory_system.menu.domain.RecipeBom;
import com.naengjang_goat.inventory_system.menu.repository.RecipeBomRepository;
import com.naengjang_goat.inventory_system.order.domain.OrderStatus;
import com.naengjang_goat.inventory_system.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 재료별 일 평균 소모량 계산 서비스.
 *
 * 알고리즘:
 *   1. 최근 PERIOD_DAYS 일간 Orders 조회
 *   2. 각 OrderItem.quantity × RecipeBom.requiredQuantity 합산 (BOM 역산)
 *   3. 단위 변환: BOM 단위 → Ingredient.baseUnit
 *   4. 합계 / PERIOD_DAYS = dailyAvgSales
 *
 * 핵심: OrderRepository.sumIngredientUsage() — DB에서 집계 (N+1 없음)
 *
 * 반환: BigDecimal (재료 baseUnit 기준). 판매 데이터 없으면 ZERO 반환.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DailySalesService {

    private static final int PERIOD_DAYS = 30;
    private static final BigDecimal PERIOD = BigDecimal.valueOf(PERIOD_DAYS);

    private final OrderRepository orderRepository;
    private final RecipeBomRepository recipeBomRepository;
    private final IngredientRepository ingredientRepository;
    private final UnitConverter unitConverter;

    /**
     * 특정 재료의 일 평균 소모량 계산.
     *
     * @param userId       점주 ID
     * @param ingredientId 재료 ID
     * @return 일 평균 소모량 (Ingredient.baseUnit 기준). 판매 데이터 없으면 ZERO.
     */
    @Transactional(readOnly = true)
    public BigDecimal getDailyAvgSales(Long userId, Long ingredientId) {
        LocalDateTime to   = LocalDateTime.now();
        LocalDateTime from = to.minusDays(PERIOD_DAYS);

        // 1. DB 집계 — SUM(orderItem.quantity × bom.requiredQuantity)
        //    CANCELLED 주문 제외. JPQL은 enum을 파라미터로 전달해야 함.
        BigDecimal rawTotal = orderRepository.sumIngredientUsage(
                userId, ingredientId, from, to, OrderStatus.CANCELED);

        if (rawTotal == null || rawTotal.compareTo(BigDecimal.ZERO) == 0) {
            log.debug("[DailySalesService] 판매 데이터 없음 userId={} ingredientId={}", userId, ingredientId);
            return BigDecimal.ZERO;
        }

        // 2. 단위 변환: BOM 단위 → baseUnit
        //    BOM 단위와 baseUnit이 동일 계열이면 UnitConverter로 변환.
        //    BOM 단위가 재료마다 다를 수 있으므로 첫 번째 BOM의 unit을 대표로 사용.
        //    (BOM 단위 혼용 시: 최초 등록 단위 기준으로 통일되어 있다고 가정)
        List<RecipeBom> boms = recipeBomRepository.findAllByIngredientIdAndUserId(ingredientId, userId);
        if (boms.isEmpty()) {
            return BigDecimal.ZERO;
        }

        Ingredient ingredient = ingredientRepository.findById(ingredientId)
                .orElse(null);
        if (ingredient == null) {
            return BigDecimal.ZERO;
        }

        String bomUnit  = boms.get(0).getUnit();
        String baseUnit = ingredient.getBaseUnit();

        BigDecimal totalInBase;
        try {
            totalInBase = unitConverter.convert(bomUnit, rawTotal, baseUnit);
        } catch (IllegalArgumentException e) {
            // 단위 변환 불가(개↔g 등) 시 그대로 사용
            log.warn("[DailySalesService] 단위 변환 실패 bomUnit={} baseUnit={}: {}",
                    bomUnit, baseUnit, e.getMessage());
            totalInBase = rawTotal;
        }

        // 3. 일 평균
        return totalInBase.divide(PERIOD, 3, RoundingMode.HALF_UP);
    }
}
