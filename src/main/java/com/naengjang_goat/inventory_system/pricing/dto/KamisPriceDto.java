package com.naengjang_goat.inventory_system.pricing.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

/** KAMIS 시세 요약 — 상세/리스트 응답에 공통. */
@Getter
@Builder
@AllArgsConstructor
public class KamisPriceDto {
    private final Long currentPricePerKg;  // 가장 최근 retail (원/kg 가정)
    private final LocalDate priceDate;
    private final Long weekAvg;            // 최근 7일 평균
    private final Long monthAvg;           // 최근 30일 평균 (v0.3 추가)
}
