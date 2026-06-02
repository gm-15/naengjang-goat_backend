package com.naengjang_goat.inventory_system.pricing.controller;

import com.naengjang_goat.inventory_system.global.security.CustomUserDetails;
import com.naengjang_goat.inventory_system.pricing.dto.LowestTopItemDto;
import com.naengjang_goat.inventory_system.pricing.dto.PriceDetailViewDto;
import com.naengjang_goat.inventory_system.pricing.dto.PriceTrendResponse;
import com.naengjang_goat.inventory_system.pricing.service.LowestTopService;
import com.naengjang_goat.inventory_system.pricing.service.PriceDetailService;
import com.naengjang_goat.inventory_system.pricing.service.PriceTrendService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * UC-CORE-2/3 메인 — KAMIS 기반 최저가 Top N + 재료 상세 + 가격 추이.
 *
 * 인증: JWT Bearer Token
 *
 * Endpoints:
 *  - GET /prices/lowest-top?limit=5          : 사장님 등록 재료 중 KAMIS 하락률 Top N
 *  - GET /prices/{ingredientId}              : 재료 상세 (KAMIS + 네이버 + 식자재왕 + 30일 추이)
 *                                              v3 응답: PriceDetailViewDto (kim ProductData 1:1)
 *  - GET /prices/{ingredientId}/trend?days=30 : 가격 추이 시계열 + buySignal (UC-CORE-3)
 */
@RestController
@RequestMapping("/prices")
@RequiredArgsConstructor
public class PriceController {

    private final LowestTopService lowestTopService;
    private final PriceDetailService priceDetailService;
    private final PriceTrendService priceTrendService;

    @GetMapping("/lowest-top")
    public ResponseEntity<List<LowestTopItemDto>> lowestTop(
            @AuthenticationPrincipal CustomUserDetails principal,
            @RequestParam(defaultValue = "5") int limit) {
        return ResponseEntity.ok(lowestTopService.getLowestTop(principal.getId(), limit));
    }

    @GetMapping("/{ingredientId}")
    public ResponseEntity<PriceDetailViewDto> detail(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable Long ingredientId) {
        return ResponseEntity.ok(priceDetailService.getDetail(principal.getId(), ingredientId));
    }

    /**
     * UC-CORE-3 — 가격 추이 시계열 + 발주 신호.
     *
     * @param days 조회 기간 (기본 30일, 최대 90일)
     */
    @GetMapping("/{ingredientId}/trend")
    public ResponseEntity<PriceTrendResponse> trend(
            @PathVariable Long ingredientId,
            @RequestParam(defaultValue = "30") int days) {
        int effectiveDays = Math.min(Math.max(days, 1), 90);
        return ResponseEntity.ok(priceTrendService.getTrend(ingredientId, effectiveDays));
    }
}
