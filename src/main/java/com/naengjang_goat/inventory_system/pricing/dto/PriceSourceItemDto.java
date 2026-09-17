package com.naengjang_goat.inventory_system.pricing.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * PriceDetailViewDto.sources[] 한 항목.
 * 프론트 타입 {@code PriceSource} 와 1:1 매핑.
 *
 * @author sim
 * @since 2026-06-01
 */
@Getter
@Builder
@AllArgsConstructor
public class PriceSourceItemDto {

    /** 채널명 — "쿠팡"|"네이버"|"마켓컬리"|"식자재왕" */
    private final String platform;

    /** 판매가 (원) */
    private final Integer price;

    /** 상품 상세 URL */
    private final String url;

    /** 채널 로고 URL (SourceLogoRegistry 매핑) */
    private final String logo;

    /** 시안의 "최저가" 뱃지용 — true면 강조 표시 */
    private final boolean isLowest;
}
