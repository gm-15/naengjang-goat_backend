package com.naengjang_goat.inventory_system.inventory.dto;

import com.naengjang_goat.inventory_system.inventory.domain.StockGrade;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * UC-CORE-1 — 재고 부족 Top N 응답 DTO (v2, 2026-04-29).
 *
 * stockRatio 공식 (확정):
 *   currentStock / (nextOrderDayDistance × dailyAvgSales)
 *
 * 등급 기준:
 *   SUFFICIENT : > 60%   (초록)
 *   NORMAL     : 30~60%  (노랑)
 *   DANGER     : ≤ 30%   (빨강) + orderAlert 대상
 *
 * dailyAvgSales == 0 (판매 데이터 없음):
 *   stockRatio = Double.MAX_VALUE → 정렬 최후순위, 알림 없음
 */
@Getter
@Builder
public class LowStockItemDto {

    private Long ingredientId;
    private String ingredientName;
    private BigDecimal currentStock;          // SUM(batch.quantity), 단위: baseUnit
    private String baseUnit;
    private BigDecimal dailyAvgSales;         // 일 평균 소모량 (baseUnit 기준)
    private int nextOrderDayDistance;         // 오늘 ~ 다음 발주일 일수
    private double stockRatio;               // currentStock / (nextOrderDayDistance × dailyAvgSales)
    private StockGrade grade;                // SUFFICIENT / NORMAL / DANGER
    private LocalDate estimatedDepletionDate; // 소진 예정일 (null = 판매 데이터 없음)
    private boolean orderAlert;              // grade==DANGER AND depletionDate < nextOrderDayDate

    /** DepletionResult → LowStockItemDto 변환 */
    public static LowStockItemDto from(DepletionResult r) {
        return LowStockItemDto.builder()
                .ingredientId(r.getIngredientId())
                .ingredientName(r.getIngredientName())
                .currentStock(r.getCurrentStock())
                .baseUnit(r.getBaseUnit())
                .dailyAvgSales(r.getDailyAvgSales())
                .nextOrderDayDistance(r.getNextOrderDayDistance())
                .stockRatio(r.getStockRatio())
                .grade(r.getGrade())
                .estimatedDepletionDate(r.getEstimatedDepletionDate())
                .orderAlert(r.isOrderAlert())
                .build();
    }
}
