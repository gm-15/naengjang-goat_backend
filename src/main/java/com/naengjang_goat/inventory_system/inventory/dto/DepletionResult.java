package com.naengjang_goat.inventory_system.inventory.dto;

import com.naengjang_goat.inventory_system.inventory.domain.StockGrade;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 재료 한 건의 소진 예측 결과.
 *
 * stockRatio 공식 (확정 2026-04-29):
 *   stockRatio = currentStock / (nextOrderDayDistance × dailyAvgSales)
 *
 *   의미: 다음 발주일까지 필요한 재고 대비 현재 보유량 비율
 *   > 1.0 (100%) → 여유
 *   0.6~1.0      → 보통
 *   < 0.3        → 위험
 *
 * dailyAvgSales == 0 이면:
 *   stockRatio = Double.MAX_VALUE (정렬 최후순위)
 *   estimatedDepletionDate = null
 *   grade = SUFFICIENT (알림 없음)
 */
@Getter
@Builder
public class DepletionResult {

    private final Long ingredientId;
    private final String ingredientName;
    private final String baseUnit;

    private final BigDecimal currentStock;           // 현재 총 재고 (SUM of InventoryBatch)
    private final BigDecimal dailyAvgSales;          // 일 평균 소모량 (baseUnit 기준)
    private final int nextOrderDayDistance;          // 오늘 ~ 다음 발주일 까지 일수

    private final double stockRatio;                 // currentStock / (nextOrderDayDistance × dailyAvgSales)
    private final StockGrade grade;                  // SUFFICIENT / NORMAL / DANGER
    private final LocalDate estimatedDepletionDate;  // today + (currentStock / dailyAvgSales), null if no sales data
    private final boolean orderAlert;                // grade==DANGER AND depletionDate < nextOrderDayDate
}
