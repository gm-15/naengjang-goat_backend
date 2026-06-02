package com.naengjang_goat.inventory_system.inventory.controller;

import com.naengjang_goat.inventory_system.global.security.CustomUserDetails;
import com.naengjang_goat.inventory_system.inventory.domain.Ingredient;
import com.naengjang_goat.inventory_system.inventory.dto.BatchResponse;
import com.naengjang_goat.inventory_system.inventory.dto.IngredientListItemDto;
import com.naengjang_goat.inventory_system.inventory.dto.LowStockItemDto;
import com.naengjang_goat.inventory_system.inventory.repository.IngredientRepository;
import com.naengjang_goat.inventory_system.inventory.repository.InventoryBatchRepository;
import com.naengjang_goat.inventory_system.inventory.service.LowStockService;
import com.naengjang_goat.inventory_system.pricing.domain.KamisCategory;
import com.naengjang_goat.inventory_system.pricing.dto.KamisPriceDto;
import com.naengjang_goat.inventory_system.pricing.service.KamisPriceCalculator;
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
 * GET /ingredients                   : 점주 재료 목록 (시안 /lowest-price 그리드) ★ sim, 2026-06-01
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

    private static final String DEFAULT_SUPPLIER_LABEL = "KAMIS 공식 시세";

    private final InventoryBatchRepository batchRepository;
    private final LowStockService lowStockService;
    private final IngredientRepository ingredientRepository;
    private final KamisPriceCalculator kamisPriceCalculator;

    /**
     * GET /ingredients
     * 점주 재료 전체 목록 — 시안 /lowest-price 그리드.
     *
     * 프론트(kim) Ingredient 타입 1:1 매핑. KAMIS 시세 포함.
     * 가격 출처(supplier) 라벨은 현재 고정 "KAMIS 공식 시세".
     *
     * @author sim
     * @since 2026-06-01
     */
    @GetMapping
    public ResponseEntity<List<IngredientListItemDto>> getIngredients(
            @AuthenticationPrincipal CustomUserDetails principal
    ) {
        List<Ingredient> ingredients = ingredientRepository
                .findAllByUserIdWithFetch(principal.getId());

        List<IngredientListItemDto> result = ingredients.stream()
                .map(this::toListItem)
                .toList();
        return ResponseEntity.ok(result);
    }

    private IngredientListItemDto toListItem(Ingredient ing) {
        KamisPriceDto kamis;
        try {
            kamis = kamisPriceCalculator.buildKamis(ing.getId());
        } catch (RuntimeException e) {
            kamis = null;
        }
        return IngredientListItemDto.builder()
                .id(ing.getId())
                .name(ing.getName())
                .category(ing.getCategory())
                .unit(ing.getBaseUnit())
                .price(kamis != null ? kamis.getCurrentPricePerKg() : null)
                .monthlyAvgPrice(kamis != null ? kamis.getMonthAvg() : null)
                .supplier(DEFAULT_SUPPLIER_LABEL)
                .image(ing.getImageUrl())
                .build();
    }

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
