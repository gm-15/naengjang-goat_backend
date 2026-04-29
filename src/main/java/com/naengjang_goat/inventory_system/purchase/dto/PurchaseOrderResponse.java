package com.naengjang_goat.inventory_system.purchase.dto;

import com.naengjang_goat.inventory_system.purchase.domain.PurchaseOrder;
import com.naengjang_goat.inventory_system.purchase.domain.PurchaseStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 발주 단건 응답 DTO.
 */
@Getter
@Builder
public class PurchaseOrderResponse {
    private final Long id;
    private final Long ingredientId;
    private final String ingredientName;
    private final LocalDate orderedAt;
    private final BigDecimal quantity;
    private final String baseUnit;
    private final BigDecimal unitPrice;
    private final BigDecimal totalAmount;
    private final String supplier;
    private final String memo;
    private final PurchaseStatus status;
    private final LocalDateTime createdAt;

    public static PurchaseOrderResponse from(PurchaseOrder po) {
        return PurchaseOrderResponse.builder()
                .id(po.getId())
                .ingredientId(po.getIngredient().getId())
                .ingredientName(po.getIngredient().getName())
                .orderedAt(po.getOrderedAt())
                .quantity(po.getQuantity())
                .baseUnit(po.getBaseUnit())
                .unitPrice(po.getUnitPrice())
                .totalAmount(po.getTotalAmount())
                .supplier(po.getSupplier())
                .memo(po.getMemo())
                .status(po.getStatus())
                .createdAt(po.getCreatedAt())
                .build();
    }
}
