package com.naengjang_goat.inventory_system.inventory.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * PATCH /ingredients/{id} 요청 본문.
 *
 * 부분 수정 의미 — null 이 아닌 필드만 업데이트.
 * 모든 필드 선택. 기존 PATCH /{id}/category 와 별개 (그건 KAMIS 카테고리 전용).
 *
 * 사용 예: 점주가 재료명 오타 ("양과" → "양파") 수정, 단위 변경, 권장 재고량 조정 등.
 *
 * @author sim
 * @since 2026-06-04
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class IngredientUpdateRequest {

    /** 재료명 (선택) */
    private String name;

    /** 기본 단위 — "g"|"ml"|"개" (선택) */
    private String baseUnit;

    /** 권장 재고량 (= warningThreshold) (선택) */
    private BigDecimal warningThreshold;

    /** UI 카테고리 라벨 — "육류"|"채소"|"소스/양념"|"유제품"|"기타" (선택) */
    private String category;

    /** 대표 이미지 URL (선택) */
    private String imageUrl;

    /** KAMIS 카테고리 — VEGETABLES|LIVESTOCK|... (선택) */
    private String kamisCategory;

    /** KAMIS item_code (선택) */
    private String kamisItemCode;
}
