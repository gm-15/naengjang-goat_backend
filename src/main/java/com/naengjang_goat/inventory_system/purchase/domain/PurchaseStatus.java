package com.naengjang_goat.inventory_system.purchase.domain;

/**
 * 발주 상태.
 *
 * PENDING   — 발주 요청(미확정)
 * CONFIRMED — 발주 확정 (기본값, Demo에서 즉시 확정)
 * CANCELLED — 취소
 */
public enum PurchaseStatus {
    PENDING,
    CONFIRMED,
    CANCELLED
}
