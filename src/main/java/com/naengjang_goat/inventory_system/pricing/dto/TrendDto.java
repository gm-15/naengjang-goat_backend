package com.naengjang_goat.inventory_system.pricing.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/** 가격 추이 요약 — UC-CORE-2 응답의 trend 필드. */
@Getter
@Builder
@AllArgsConstructor
public class TrendDto {
    private final Long high;
    private final Long current;
    private final Long low;
    private final Integer windowDays;
}
