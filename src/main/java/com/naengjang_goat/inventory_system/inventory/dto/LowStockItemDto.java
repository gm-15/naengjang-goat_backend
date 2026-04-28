package com.naengjang_goat.inventory_system.inventory.dto;

import com.naengjang_goat.inventory_system.inventory.domain.Ingredient;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * UC-CORE-1 — 재고 부족 Top N 응답 DTO.
 *
 * stockRatio: 현재 재고 / 기준 재고 (낮을수록 긴박)
 *   - 0.0 = 재고 없음
 *   - 0.5 = 기준량의 절반
 *   - 1.0+ = 여유
 * isAlert: stockRatio < 1.0 (기준 미달)
 *
 * warningThreshold 미설정 재료는 정렬 최후순위 (stockRatio = Double.MAX_VALUE).
 */
@Getter
@Builder
public class LowStockItemDto {

    private Long ingredientId;
    private String ingredientName;
    private BigDecimal currentStock;  // SUM(batch.quantity), 단위: baseUnit
    private String baseUnit;
    private BigDecimal warningThreshold;  // null 가능 (미설정)
    private double stockRatio;            // currentStock / warningThreshold
    private boolean alert;                // stockRatio < 1.0

    public static LowStockItemDto of(Ingredient ingredient, BigDecimal currentStock) {
        BigDecimal threshold = ingredient.getWarningThreshold();

        double ratio;
        boolean isAlert;

        if (threshold == null || threshold.compareTo(BigDecimal.ZERO) <= 0) {
            // 기준 미설정 → 정렬 최후순위, 알림 없음
            ratio = Double.MAX_VALUE;
            isAlert = false;
        } else {
            ratio = currentStock.divide(threshold, 4, RoundingMode.HALF_UP).doubleValue();
            isAlert = ratio < 1.0;
        }

        return LowStockItemDto.builder()
                .ingredientId(ingredient.getId())
                .ingredientName(ingredient.getName())
                .currentStock(currentStock)
                .baseUnit(ingredient.getBaseUnit())
                .warningThreshold(threshold)
                .stockRatio(ratio)
                .alert(isAlert)
                .build();
    }
}
