package com.naengjang_goat.inventory_system.inventory.dto;

import com.naengjang_goat.inventory_system.inventory.domain.StockGrade;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * UC-CORE-1 — 재고 부족 Top N 응답 DTO (v2, 2026-04-29).
 *
 * stockRatio 공식 (확정):
 *   currentStock / (nextOrderDayDistance × dailyAvgSales)
 *
 * 등급 기준:
 *   SUFFICIENT : > 60%   (초록)
 *   NORMAL     : 30~60%  (노랑)
 *   DANGER     : ≤ 30%   (빨강) + orderAlert 대상
 *
 * dailyAvgSales == 0 (판매 데이터 없음):
 *   stockRatio = Double.MAX_VALUE → 정렬 최후순위, 알림 없음
 */
@Getter
@Builder(toBuilder = true)
public class LowStockItemDto {

    private Long ingredientId;
    private String ingredientName;
    private BigDecimal currentStock;          // SUM(batch.quantity), 단위: baseUnit
    private String baseUnit;
    private BigDecimal dailyAvgSales;         // 일 평균 소모량 (baseUnit 기준)
    private int nextOrderDayDistance;         // 오늘 ~ 다음 발주일 일수
    private double stockRatio;               // currentStock / (nextOrderDayDistance × dailyAvgSales)
    private StockGrade grade;                // SUFFICIENT / NORMAL / DANGER
    private LocalDate estimatedDepletionDate; // 소진 예정일 (null = 판매 데이터 없음)
    private boolean orderAlert;              // grade==DANGER AND depletionDate < nextOrderDayDate

    // ─── sim, 2026-06-01 — 시안 발주 페이지 카드 매핑 추가 필드 ─────────────────
    /** UI 카테고리 라벨 — "육류"|"채소"|"소스/양념"|"유제품"|"기타" (nullable) */
    private String category;
    /** 대표 이미지 URL (nullable) */
    private String image;
    /** 권장 재고량 — Ingredient.warningThreshold. 시안 "권장: 50kg" */
    private BigDecimal warningThreshold;
    /**
     * 시안 친화 재고율 = currentStock / warningThreshold (0.0~1.0+).
     * v2 stockRatio (현재/예상소비) 와 다른 의미 — 시안 카드 "재고율 10%" 라벨용.
     * warningThreshold 가 0 이거나 null 이면 null.
     */
    private Double simpleStockRatio;
    /** 가장 최근 발주 일자 (PurchaseOrder.orderedAt MAX). 없으면 null. 시안 "마지막 발주" */
    private LocalDate lastOrderedAt;
    // ─────────────────────────────────────────────────────────────────────────

    /** DepletionResult → LowStockItemDto 변환 (기존 호환). 시안 필드는 호출처에서 채움. */
    public static LowStockItemDto from(DepletionResult r) {
        return LowStockItemDto.builder()
                .ingredientId(r.getIngredientId())
                .ingredientName(r.getIngredientName())
                .currentStock(r.getCurrentStock())
                .baseUnit(r.getBaseUnit())
                .dailyAvgSales(r.getDailyAvgSales())
                .nextOrderDayDistance(r.getNextOrderDayDistance())
                .stockRatio(r.getStockRatio())
                .grade(r.getGrade())
                .estimatedDepletionDate(r.getEstimatedDepletionDate())
                .orderAlert(r.isOrderAlert())
                .build();
    }
}
