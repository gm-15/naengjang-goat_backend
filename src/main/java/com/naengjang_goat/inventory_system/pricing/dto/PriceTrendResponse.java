package com.naengjang_goat.inventory_system.pricing.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * GET /prices/{ingredientId}/trend 응답 래퍼.
 *
 * currentBuySignal : 마지막(오늘) 포인트의 buySignal 요약
 * signalReason     : 가격 대비 % 설명 문자열
 * dataCoverage     : 실제 반환된 데이터 포인트 수 (요청 days 보다 적을 수 있음)
 * points           : 날짜 오름차순 시계열 목록
 */
@Getter
@Builder
public class PriceTrendResponse {
    private final Long ingredientId;
    private final String ingredientName;
    private final boolean currentBuySignal;
    private final String signalReason;
    private final int dataCoverage;
    private final List<TrendPointDto> points;
}
