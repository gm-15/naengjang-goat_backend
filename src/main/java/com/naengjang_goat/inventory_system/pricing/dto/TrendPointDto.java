package com.naengjang_goat.inventory_system.pricing.dto;

import java.time.LocalDate;

/**
 * 가격 추이 시계열 단일 포인트.
 *
 * weekAvg / monthAvg : 해당 포인트까지의 슬라이딩 윈도우 평균 (wholesalePrice 기준, null이면 retailPrice 대체)
 * buySignal          : currentPrice < monthAvg × (1 - category.buySignalThreshold)
 */
public record TrendPointDto(
        LocalDate date,
        Long wholesalePrice,  // 도매가 (발주 의사결정 기준), null 가능
        Long retailPrice,     // 소매가 (참고용), null 가능
        Long weekAvg,         // 직전 7일 이동평균 (null = 데이터 부족)
        Long monthAvg,        // 직전 30일 이동평균 (null = 데이터 부족)
        boolean buySignal     // 매수 신호 여부
) {}
