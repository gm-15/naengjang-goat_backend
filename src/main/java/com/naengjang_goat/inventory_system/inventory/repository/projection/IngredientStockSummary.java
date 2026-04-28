package com.naengjang_goat.inventory_system.inventory.repository.projection;

import java.math.BigDecimal;

/**
 * InventoryBatch 집계 결과 프로젝션.
 * InventoryBatchRepository.findStockSummaryByUserId() 반환 타입.
 */
public interface IngredientStockSummary {
    Long getIngredientId();
    BigDecimal getTotalQuantity();
}
