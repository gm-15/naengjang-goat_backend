package com.naengjang_goat.inventory_system.pricing.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/** /prices/lowest-top 응답 한 항목. */
@Getter
@Builder
@AllArgsConstructor
public class LowestTopItemDto {
    private final Long ingredientId;
    private final String name;
    /** 재료 기본 관리 단위 (g · ml · 개). 재고 배치 수량이 이 단위로 저장된다. */
    private final String unit;
    private final Long weekAvg;
    private final Long monthAvg;
    private final Long todayPrice;
    private final Double dropRatePct;
    private final TrendDto trend;
    private final List<ExternalLinkDto> externalLinks;
}
