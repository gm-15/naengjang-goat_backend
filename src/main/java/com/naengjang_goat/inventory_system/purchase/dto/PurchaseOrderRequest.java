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
    @jakarta.validation.constraints.NotNull
    private Long ingredientId;
    private LocalDate orderedAt;
    @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.Positive @jakarta.validation.constraints.Digits(integer=7,fraction=3)
    private BigDecimal quantity;
    @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max=20)
    private String baseUnit;
    @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.DecimalMin("0") @jakarta.validation.constraints.Digits(integer=8,fraction=2)
    private BigDecimal unitPrice;
    @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max=100)
    private String supplier;
    @jakarta.validation.constraints.Size(max=4096) private String memo;
    @jakarta.validation.constraints.Size(max=255) private String productName;
    @jakarta.validation.constraints.Size(max=2048) private String sourceUrl;
    private BigDecimal packageSize;
    private String packageUnit;
}
