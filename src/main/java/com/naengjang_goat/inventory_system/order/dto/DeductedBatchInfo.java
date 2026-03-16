package com.naengjang_goat.inventory_system.order.dto;

import java.math.BigDecimal;

/**
 * 주문 처리 시 FIFO로 차감된 배치 정보 (v2.1)
 * POST /orders 응답의 deductedBatches 항목.
 * POS 화면에서 "재고 배치 X에서 Y만큼 사용" 표시 용도.
 */
public record DeductedBatchInfo(
        Long batchId,
        BigDecimal deductedQuantity
) {}
