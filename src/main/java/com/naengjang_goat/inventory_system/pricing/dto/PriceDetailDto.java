package com.naengjang_goat.inventory_system.pricing.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * GET /prices/{ingredientId} 응답.
 *
 * BR2-11: onlinePrices 가 비어있으면 externalSearchLinks 노출 (UI fallback).
 */
@Getter
@Builder
@AllArgsConstructor
public class PriceDetailDto {
    private final Long ingredientId;
    private final String name;
    private final String unit;
    private final KamisPriceDto kamis;
    private final List<OnlinePriceDto> onlinePrices;
    private final List<ExternalLinkDto> externalSearchLinks;
}
