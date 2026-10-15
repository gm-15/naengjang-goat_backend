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
    private final Long currentPricePerKg;  // priceBasis에 해당하는 실제 최근 관측, 원/kg
    private final LocalDate priceDate;
    private final Long weekAvg;            // 최근 7일 평균
    private final Long monthAvg;           // 최근 30일 평균 (v0.3 추가)
    private final String priceBasis;
    private final String priceSource;
    private final String kindName;
    private final String grade;
    private final String unit;
}
