package com.naengjang_goat.inventory_system.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * POST /ingredients 요청 본문.
 *
 * kim 시안 /lowest-price "+ 재료 추가" 모달 입력 데이터.
 *
 * @author sim
 * @since 2026-06-01
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class IngredientCreateRequest {

    /** 재료명 (필수) */
    @NotBlank
    private String name;

    /** 기본 단위 — "g"|"ml"|"개" (필수) */
    @NotBlank
    private String baseUnit;

    /** 권장 재고량 (= warningThreshold). 미지정 시 0 으로 저장 */
    private BigDecimal warningThreshold;

    /** KAMIS 카테고리 — VEGETABLES|LIVESTOCK|SEAFOOD|FRUITS|GRAINS|PROCESSED (선택) */
    private String kamisCategory;

    /** UI 카테고리 라벨 — "육류"|"채소"|"소스/양념"|"유제품"|"기타" (선택) */
    private String category;

    /** 대표 이미지 URL (선택) */
    private String imageUrl;
}
