package com.naengjang_goat.inventory_system.pricing.controller;

import com.naengjang_goat.inventory_system.pricing.dto.LowestTopItemDto;
import com.naengjang_goat.inventory_system.pricing.dto.PriceDetailDto;
import com.naengjang_goat.inventory_system.pricing.service.LowestTopService;
import com.naengjang_goat.inventory_system.pricing.service.PriceDetailService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * UC-CORE-2 메인 — KAMIS 기반 최저가 Top N + 재료 상세.
 *
 * 인증: MockAuthFilter 의 X-User-Id 헤더 사용 (Demo).
 *
 * Endpoints:
 *  - GET /prices/lowest-top?limit=5 : 사장님 등록 재료 중 KAMIS 하락률 Top N
 *  - GET /prices/{ingredientId}    : 재료 상세 (KAMIS + 네이버 + 식자재왕)
 */
@RestController
@RequestMapping("/prices")
@RequiredArgsConstructor
public class PriceController {

    private final LowestTopService lowestTopService;
    private final PriceDetailService priceDetailService;

    @GetMapping("/lowest-top")
    public ResponseEntity<List<LowestTopItemDto>> lowestTop(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam(defaultValue = "5") int limit) {
        return ResponseEntity.ok(lowestTopService.getLowestTop(userId, limit));
    }

    @GetMapping("/{ingredientId}")
    public ResponseEntity<PriceDetailDto> detail(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long ingredientId) {
        return ResponseEntity.ok(priceDetailService.getDetail(userId, ingredientId));
    }
}
