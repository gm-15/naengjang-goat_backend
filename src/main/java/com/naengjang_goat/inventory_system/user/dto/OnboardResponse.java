package com.naengjang_goat.inventory_system.user.dto;

import java.util.List;

/**
 * POST /api/users/onboard 응답 DTO.
 *
 * createdMenus          : 생성된 메뉴 수
 * createdBom            : 생성된 BOM(재료 구성) 행 수
 * newIngredients        : 기존 재료 없어 새로 생성된 재료명 목록
 *                         (requiredQuantity=1 placeholder — 사장님 직접 수정 필요)
 */
public record OnboardResponse(
        int createdMenus,
        int createdBom,
        List<String> newIngredients
) {}
