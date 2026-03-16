package com.naengjang_goat.inventory_system.order.dto;

import com.naengjang_goat.inventory_system.order.domain.ChannelType;
import com.naengjang_goat.inventory_system.order.domain.Order;
import com.naengjang_goat.inventory_system.order.domain.OrderStatus;

import java.time.LocalDateTime;
import java.util.List;

public record OrderResponse(
        Long orderId,
        ChannelType channelType,
        OrderStatus orderStatus,
        Integer totalAmount,
        LocalDateTime createdAt,
        List<DeductedBatchInfo> deductedBatches
) {
    /** GET /orders 이력 조회 — 배치 정보 없이 */
    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getChannelType(),
                order.getOrderStatus(),
                order.getTotalAmount(),
                order.getCreatedAt(),
                List.of()
        );
    }

    /** POST /orders 주문 처리 — 차감 배치 정보 포함 */
    public static OrderResponse from(Order order, List<DeductedBatchInfo> deductedBatches) {
        return new OrderResponse(
                order.getId(),
                order.getChannelType(),
                order.getOrderStatus(),
                order.getTotalAmount(),
                order.getCreatedAt(),
                deductedBatches
        );
    }
}
