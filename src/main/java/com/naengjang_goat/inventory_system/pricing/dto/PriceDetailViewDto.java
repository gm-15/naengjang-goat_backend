package com.naengjang_goat.inventory_system.pricing.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * GET /prices/{ingredientId} 응답 (v3 — 프론트(kim) ProductData 1:1 매핑).
 *
 * 변경 이유:
 *  - 기존 {@link PriceDetailDto} 는 중첩 구조(kamis.* / onlinePrices.*)였음.
 *  - 프론트 타입(`src/app/types/ingredient.ts > ProductData`)은 납작 구조 + 단순 키.
 *  - 두 쪽 모두 만족시키기 위해 신규 응답 DTO 도입.
 *
 * 프론트 매핑:
 *  id, name, category, image, kamisPrice, kamisDate, sources[], priceHistory[]
 *
 * @author sim
 * @since 2026-06-01
 */
@Getter
@Builder
@AllArgsConstructor
public class PriceDetailViewDto {

    /** 재료 ID (프론트 ProductData.id) */
    private final Long id;

    /** 재료명 */
    private final String name;

    /** 카테고리 라벨 — "육류"|"채소"|"소스/양념"|"유제품"|"기타" */
    private final String category;

    /** 재료 대표 이미지 URL */
    private final String image;

    /** KAMIS 현재가 (원/단위) — null 가능 */
    private final Long kamisPrice;

    /** KAMIS 기준일 (ISO yyyy-MM-dd) — null 가능 */
    private final String kamisDate;

    /** 단위 (kg, L, 개 등) — 시안의 "/ kg" 표기용 */
    private final String unit;

    /** 온라인 채널별 최저가 목록 (쿠팡·네이버·마켓컬리·식자재왕) */
    private final List<PriceSourceItemDto> sources;

    /** 30일 가격 추이 — 그래프용 */
    private final List<PricePointItemDto> priceHistory;
}
