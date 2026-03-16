package com.naengjang_goat.inventory_system.inventory.controller;

import com.naengjang_goat.inventory_system.inventory.dto.BatchResponse;
import com.naengjang_goat.inventory_system.inventory.repository.InventoryBatchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * 재료 재고 배치 조회 컨트롤러 (v2.1)
 * MockAuthFilter가 X-User-Id 헤더를 request attribute로 주입함.
 */
@RestController
@RequestMapping("/ingredients")
@RequiredArgsConstructor
public class IngredientController {

    private final InventoryBatchRepository batchRepository;

    /**
     * GET /ingredients/{id}/batches
     * 특정 재료의 잔여 재고 배치 목록 조회 (FIFO 순).
     * quantity > 0인 배치만 반환.
     *
     * Response: [ { "batchId": 1, "quantity": 500.000, "expiresAt": "2026-04-01" }, ... ]
     * Header:   X-User-Id: 1
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
}
