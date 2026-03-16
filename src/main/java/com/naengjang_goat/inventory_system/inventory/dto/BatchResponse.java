package com.naengjang_goat.inventory_system.inventory.dto;

import com.naengjang_goat.inventory_system.inventory.domain.InventoryBatch;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * GET /ingredients/{id}/batches 응답 DTO (v2.1)
 * POS 화면 재고 현황 표시용.
 */
public record BatchResponse(
        Long batchId,
        BigDecimal quantity,
        LocalDate expiresAt
) {
    public static BatchResponse from(InventoryBatch batch) {
        return new BatchResponse(
                batch.getId(),
                batch.getQuantity(),
                batch.getExpirationDate()
        );
    }
}
