package com.naengjang_goat.inventory_system.order.domain;

/**
 * 주문 상태 (v2.1)
 * CANCELED 시 재고 복구 로직(InventoryBatch quantity 복원) 필요
 */
public enum OrderStatus {
    COMPLETED, // 주문 완료 — 재고 차감 완료
    CANCELED   // 주문 취소 — 재고 복구 필요
}
