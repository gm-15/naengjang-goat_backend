package com.naengjang_goat.inventory_system.pricing.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * PriceDetailViewDto.priceHistory[] 한 항목.
 * 프론트 타입 {@code PricePoint} 와 1:1 매핑 (시안 30일 그래프용).
 *
 * @author sim
 * @since 2026-06-01
 */
@Getter
@Builder
@AllArgsConstructor
public class PricePointItemDto {

    /** 기준일 (ISO yyyy-MM-dd) */
    private final String date;

    /** 가격 (원) — wholesalePrice 우선, 없으면 retailPrice */
    private final Long price;
}
