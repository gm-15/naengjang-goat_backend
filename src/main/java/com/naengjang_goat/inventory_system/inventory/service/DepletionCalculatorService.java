package com.naengjang_goat.inventory_system.inventory.service;

import com.naengjang_goat.inventory_system.inventory.domain.Ingredient;
import com.naengjang_goat.inventory_system.inventory.domain.StockGrade;
import com.naengjang_goat.inventory_system.inventory.dto.DepletionResult;
import com.naengjang_goat.inventory_system.inventory.repository.IngredientRepository;
import com.naengjang_goat.inventory_system.inventory.repository.InventoryBatchRepository;
import com.naengjang_goat.inventory_system.settings.domain.StoreSettings;
import com.naengjang_goat.inventory_system.settings.service.StoreSettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;

/**
 * 재료별 소진 예정일 + stockRatio 계산 서비스.
 *
 * stockRatio 공식 (확정 2026-04-29):
 *   stockRatio = currentStock / (nextOrderDayDistance × dailyAvgSales)
 *   의미: 다음 발주일까지 필요한 재고 대비 현재 보유량
 *
 * nextOrderDayDistance:
 *   오늘부터 StoreSettings.orderDay 까지 남은 일수
 *   오늘 == orderDay → 7일 (이번 주 발주일은 오늘이므로 다음 주 기준)
 *
 * estimatedDepletionDate:
 *   today + ceil(currentStock / dailyAvgSales)
 *   dailyAvgSales == 0 → null (판매 데이터 부족)
 *
 * orderAlert 조건:
 *   grade == DANGER AND estimatedDepletionDate < nextOrderDayDate
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DepletionCalculatorService {

    private final DailySalesService dailySalesService;
    private final InventoryBatchRepository batchRepository;
    private final IngredientRepository ingredientRepository;
    private final StoreSettingsService settingsService;

    @Transactional(readOnly = true)
    public DepletionResult calculate(Long userId, Long ingredientId) {
        return calculate(userId, ingredientId, calcNextOrderDayDistance(userId));
    }

    /**
     * nextOrderDayDistance 를 외부에서 미리 계산해서 넘기는 오버로드.
     * LowStockService 배치 처리처럼 동일 userId 로 N번 호출할 때 DB 조회를 1회로 줄임.
     */
    @Transactional(readOnly = true)
    public DepletionResult calculate(Long userId, Long ingredientId, int nextOrderDayDistance) {

        // 1. 재료 기본 정보
        Ingredient ingredient = ingredientRepository.findById(ingredientId)
                .orElseThrow(() -> new IllegalArgumentException("재료 없음: " + ingredientId));

        // 2. 현재 총 재고
        BigDecimal currentStock = batchRepository.sumQuantityByIngredientId(ingredientId);
        if (currentStock == null) currentStock = BigDecimal.ZERO;

        // 3. 일 평균 소모량
        BigDecimal dailyAvgSales = dailySalesService.getDailyAvgSales(userId, ingredientId);

        // 4. stockRatio 계산
        double stockRatio;
        if (dailyAvgSales.compareTo(BigDecimal.ZERO) == 0 || nextOrderDayDistance == 0) {
            // 판매 데이터 없음 → 정렬 최후순위, 알림 없음
            stockRatio = Double.MAX_VALUE;
        } else {
            BigDecimal denominator = dailyAvgSales.multiply(BigDecimal.valueOf(nextOrderDayDistance));
            stockRatio = currentStock.divide(denominator, 4, RoundingMode.HALF_UP).doubleValue();
        }

        // 5. 재고 등급
        StockGrade grade = (stockRatio == Double.MAX_VALUE) ? StockGrade.SUFFICIENT : StockGrade.from(stockRatio);

        // 6. 소진 예정일
        LocalDate estimatedDepletionDate = null;
        if (dailyAvgSales.compareTo(BigDecimal.ZERO) > 0) {
            long depletionDays = currentStock
                    .divide(dailyAvgSales, 0, RoundingMode.CEILING)
                    .longValue();
            estimatedDepletionDate = LocalDate.now().plusDays(depletionDays);
        }

        // 7. 발주 알림 여부
        boolean orderAlert = false;
        if (grade == StockGrade.DANGER && estimatedDepletionDate != null) {
            LocalDate nextOrderDate = LocalDate.now().plusDays(nextOrderDayDistance);
            orderAlert = estimatedDepletionDate.isBefore(nextOrderDate);
        }

        return DepletionResult.builder()
                .ingredientId(ingredientId)
                .ingredientName(ingredient.getName())
                .baseUnit(ingredient.getBaseUnit())
                .currentStock(currentStock)
                .dailyAvgSales(dailyAvgSales)
                .nextOrderDayDistance(nextOrderDayDistance)
                .stockRatio(stockRatio)
                .grade(grade)
                .estimatedDepletionDate(estimatedDepletionDate)
                .orderAlert(orderAlert)
                .build();
    }

    /**
     * 오늘부터 다음 발주 요일까지 남은 일수.
     * 오늘이 발주일이면 7일 (이번 주 발주는 오늘 완료 → 다음 주 기준).
     * StoreSettings 미설정 시 기본값 7일 반환.
     */
    public int calcNextOrderDayDistance(Long userId) {
        StoreSettings settings;
        try {
            settings = settingsService.getSettingsOrThrow(userId);
        } catch (IllegalStateException e) {
            log.warn("[DepletionCalculator] StoreSettings 미설정 userId={} → 기본값 7일", userId);
            return 7;
        }

        DayOfWeek today     = LocalDate.now().getDayOfWeek();
        DayOfWeek orderDay  = settings.getOrderDay().toJavaDayOfWeek();

        int diff = orderDay.getValue() - today.getValue();
        if (diff <= 0) diff += 7; // 이미 지났거나 오늘이면 다음 주로
        return diff;
    }
}
