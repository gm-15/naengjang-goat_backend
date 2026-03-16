package com.naengjang_goat.inventory_system.order.dto;

import com.naengjang_goat.inventory_system.order.domain.ChannelType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * POS 프론트엔드용 단순화 주문 요청 DTO (v2.1)
 * 단일 메뉴를 한 번의 주문으로 처리.
 *
 * POST /orders
 * { "menuId": 1, "quantity": 2, "channelType": "POS" }
 */
public record PosOrderRequest(
        @NotNull Long menuId,
        @NotNull @Positive Integer quantity,
        @NotNull ChannelType channelType
) {}
