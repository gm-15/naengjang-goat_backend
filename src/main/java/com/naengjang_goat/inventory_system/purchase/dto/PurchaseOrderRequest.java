package com.naengjang_goat.inventory_system.purchase.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * POST /purchase-orders 요청 바디.
 *
 * orderedAt null이면 서비스에서 오늘 날짜로 대체.
 * totalAmount는 서비스에서 quantity × unitPrice로 자동 계산.
 */
@Getter
@NoArgsConstructor
public class PurchaseOrderRequest {
    private Long ingredientId;
    private LocalDate orderedAt;
    private BigDecimal quantity;
    private String baseUnit;
    private BigDecimal unitPrice;
    private String supplier;
    private String memo;
}
