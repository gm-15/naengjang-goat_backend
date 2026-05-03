package com.naengjang_goat.inventory_system.inventory.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * POST /inventory/batches 요청 DTO.
 *
 * - ingredientId  : 점주 소유 재료 ID (필수)
 * - quantity      : 입고 수량 (필수, 양수)
 * - costPerUnit   : 입고 단가 (선택)
 * - inboundDate   : 입고 날짜 (선택, 생략 시 오늘)
 * - expirationDate: 유통기한 (필수)
 */
public record BatchRequest(
        @NotNull Long ingredientId,
        @NotNull @Positive BigDecimal quantity,
        BigDecimal costPerUnit,
        LocalDate inboundDate,
        @NotNull LocalDate expirationDate
) {}
