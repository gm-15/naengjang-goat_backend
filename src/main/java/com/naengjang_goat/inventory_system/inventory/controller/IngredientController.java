package com.naengjang_goat.inventory_system.inventory.controller;

import com.naengjang_goat.inventory_system.global.security.CustomUserDetails;
import com.naengjang_goat.inventory_system.inventory.dto.BatchResponse;
import com.naengjang_goat.inventory_system.inventory.dto.LowStockItemDto;
import com.naengjang_goat.inventory_system.inventory.repository.IngredientRepository;
import com.naengjang_goat.inventory_system.inventory.repository.InventoryBatchRepository;
import com.naengjang_goat.inventory_system.inventory.service.LowStockService;
import com.naengjang_goat.inventory_system.pricing.domain.KamisCategory;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * 재료 재고 배치 조회 컨트롤러 (v2.2 — JWT 인증 복구)
 *
 * GET /ingredients/{id}/batches      : 재료별 잔여 배치 목록
 * GET /ingredients/low-stock?limit=5 : UC-CORE-1 재고 부족 Top N
 * PATCH /ingredients/{id}/category   : KAMIS 카테고리 설정
 *
 * 인증: JWT Bearer Token
 */
@RestController
@RequestMapping("/ingredients")
@RequiredArgsConstructor
public class IngredientController {

    private final InventoryBatchRepository batchRepository;
    private final LowStockService lowStockService;
    private final IngredientRepository ingredientRepository;

    /**
     * GET /ingredients/{id}/batches
     * 특정 재료의 잔여 재고 배치 목록 조회 (FIFO 순, quantity > 0).
     */
    @GetMapping("/{id}/batches")
    public ResponseEntity<List<BatchResponse>> getBatches(
            @PathVariable Long id
    ) {
        List<BatchResponse> batches =
                batchRepository.findAllByIngredientIdAndQuantityGreaterThanOrderByExpirationDateAsc(
                                id, BigDecimal.ZERO)
                        .stream()
                        .map(BatchResponse::from)
                        .toList();
        return ResponseEntity.ok(batches);
    }

    /**
     * GET /ingredients/low-stock?limit=5
     * UC-CORE-1 — 재고 부족 상위 N개 재료 반환 (JWT 토큰에서 userId 추출).
     *
     * 정렬: stockRatio 오름차순 (낮을수록 위험)
     */
    @GetMapping("/low-stock")
    public ResponseEntity<List<LowStockItemDto>> getLowStock(
            @AuthenticationPrincipal CustomUserDetails principal,
            @RequestParam(defaultValue = "5") int limit
    ) {
        List<LowStockItemDto> result = lowStockService.getTopLowStock(principal.getId(), limit);
        return ResponseEntity.ok(result);
    }

    /**
     * PATCH /ingredients/{id}/category?category=VEGETABLES
     * UC-CORE-3 — 재료의 KAMIS 카테고리 설정 (buySignal 임계값 결정).
     *
     * category 유효값: VEGETABLES / LIVESTOCK / SEAFOOD / FRUITS / GRAINS / PROCESSED
     */
    @PatchMapping("/{id}/category")
    public ResponseEntity<Void> updateKamisCategory(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable Long id,
            @RequestParam String category) {

        try {
            KamisCategory.valueOf(category);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "유효하지 않은 카테고리. 허용값: " + java.util.Arrays.toString(KamisCategory.values()));
        }

        var ingredient = ingredientRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "재료 없음: " + id));

        if (!ingredient.getUser().getId().equals(principal.getId())) {
            throw new ResponseStatusException(FORBIDDEN, "접근 권한 없음");
        }

        ingredient.setKamisCategory(category);
        ingredientRepository.save(ingredient);
        return ResponseEntity.ok().build();
    }
}
