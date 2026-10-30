package com.naengjang_goat.inventory_system.pricing.dto;

/**
 * 홈 화면 "오늘 평소보다 싼 재료" 모달 응답.
 * BuySignalNotifyTasklet 과 동일 공식: current < monthAvg × (1 - threshold).
 * dropPercent 가장 큰 재료 1개만 반환.
 */
public record BuySignalTodayDto(
    Long ingredientId,
    String ingredientName,
    String category,
    Long monthAvg,
    Long currentPrice,
    Double dropPercent
) {}
