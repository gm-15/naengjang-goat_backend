package com.naengjang_goat.inventory_system.purchase.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

/**
 * GET /purchase-orders/summary 응답.
 *
 * CANCELLED 발주는 집계에서 제외.
 * byIngredient: totalAmount 내림차순 정렬.
 */
@Getter
@Builder
public class PurchaseOrderSummaryDto {
    private final int totalCount;
    private final BigDecimal totalAmount;
    private final List<ByIngredientDto> byIngredient;

    public record ByIngredientDto(
            String ingredientName,
            int count,
            BigDecimal totalAmount
    ) {}
}
