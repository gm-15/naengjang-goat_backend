package com.naengjang_goat.inventory_system.purchase.controller;

import com.naengjang_goat.inventory_system.global.security.CustomUserDetails;
import com.naengjang_goat.inventory_system.purchase.domain.PurchaseStatus;
import com.naengjang_goat.inventory_system.purchase.dto.PurchaseOrderRequest;
import com.naengjang_goat.inventory_system.purchase.dto.PurchaseOrderResponse;
import com.naengjang_goat.inventory_system.purchase.dto.PurchaseOrderSummaryDto;
import com.naengjang_goat.inventory_system.purchase.service.PurchaseOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * UC-SUP-8 — 발주 이력 CRUD.
 *
 * 인증: JWT Bearer Token
 *
 * POST   /purchase-orders                 — 발주 등록
 * GET    /purchase-orders                 — 발주 목록 (기간·재료·상태 필터, 페이지네이션)
 * GET    /purchase-orders/summary         — 기간별 발주 합계 + 재료별 분류
 */
@RestController
@RequestMapping("/purchase-orders")
@RequiredArgsConstructor
public class PurchaseOrderController {

    private final PurchaseOrderService purchaseOrderService;

    @PostMapping
    public ResponseEntity<PurchaseOrderResponse> create(
            @AuthenticationPrincipal CustomUserDetails principal,
            @RequestBody PurchaseOrderRequest request) {
        return ResponseEntity.ok(purchaseOrderService.create(principal.getId(), request));
    }

    @GetMapping
    public ResponseEntity<Page<PurchaseOrderResponse>> list(
            @AuthenticationPrincipal CustomUserDetails principal,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long ingredientId,
            @RequestParam(required = false) PurchaseStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(
                purchaseOrderService.list(principal.getId(), from, to, ingredientId, status, page, size));
    }

    @GetMapping("/summary")
    public ResponseEntity<PurchaseOrderSummaryDto> summary(
            @AuthenticationPrincipal CustomUserDetails principal,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(purchaseOrderService.summary(principal.getId(), from, to));
    }
}
