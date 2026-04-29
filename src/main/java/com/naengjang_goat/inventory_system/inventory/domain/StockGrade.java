package com.naengjang_goat.inventory_system.inventory.domain;

/**
 * 재고 위험 등급 (확정 2026-04-29).
 *
 * 기준: stockRatio = currentStock / (nextOrderDayDistance × dailyAvgSales)
 *
 *   SUFFICIENT : stockRatio > 0.60  → 다음 발주일까지 여유 있음 (초록)
 *   NORMAL     : 0.30 < ratio ≤ 0.60 → 주의 필요 (노랑)
 *   DANGER     : ratio ≤ 0.30        → 발주 알림 대상 (빨강)
 *
 * dailyAvgSales == 0 (판매 데이터 없음) → SUFFICIENT 처리 (알림 없음).
 */
public enum StockGrade {

    SUFFICIENT,   // > 60%  (초록)
    NORMAL,       // 30~60% (노랑)
    DANGER;       // ≤ 30%  (빨강)

    public static StockGrade from(double stockRatio) {
        if (stockRatio > 0.60) return SUFFICIENT;
        if (stockRatio > 0.30) return NORMAL;
        return DANGER;
    }
}
