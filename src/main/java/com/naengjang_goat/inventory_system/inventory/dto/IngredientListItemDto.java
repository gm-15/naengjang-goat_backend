package com.naengjang_goat.inventory_system.inventory.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * GET /ingredients 응답 한 항목.
 * 시안 /lowest-price (재료 목록 그리드) 카드 1장에 매핑.
 *
 * 프론트(kim) {@code Ingredient} 타입과 1:1 매핑:
 *   id, name, category, price, unit, supplier, monthlyAvgPrice (+ image)
 *
 * @author sim
 * @since 2026-06-01
 */
@Getter
@Builder
@AllArgsConstructor
public class IngredientListItemDto {

    /** 재료 ID */
    private final Long id;

    /** 재료명 */
    private final String name;

    /** UI 카테고리 — "육류"|"채소"|"소스/양념"|"유제품"|"기타" (nullable) */
    private final String category;

    /** 단위 — kg|g|L|ml|개 */
    private final String unit;

    /** 현재 KAMIS 시세 (원/kg). 시세 없으면 null */
    private final Long price;

    /** 가격 출처 라벨 (시안 "A업체" 슬롯). 현재는 고정 "KAMIS 공식 시세". */
    private final String supplier;

    /** 30일 평균가 (원/kg). 데이터 부족 시 null */
    private final Long monthlyAvgPrice;

    /** 대표 이미지 URL (시안 카드 좌측 썸네일용, nullable) */
    private final String image;
}
