package com.naengjang_goat.inventory_system.purchase.controller;

import com.naengjang_goat.inventory_system.global.security.CustomUserDetails;
import com.naengjang_goat.inventory_system.purchase.domain.PurchaseStatus;
import com.naengjang_goat.inventory_system.purchase.dto.PurchaseOrderRequest;
import com.naengjang_goat.inventory_system.purchase.dto.PurchaseOrderResponse;
import com.naengjang_goat.inventory_system.purchase.dto.PurchaseOrderSummaryDto;
import com.naengjang_goat.inventory_system.purchase.service.PurchaseOrderExcelService;
import com.naengjang_goat.inventory_system.purchase.service.PurchaseOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * UC-SUP-8 — 발주 이력 CRUD.
 *
 * 인증: JWT Bearer Token
 *
 * POST   /purchase-orders                 — 발주 등록
 * GET    /purchase-orders                 — 발주 목록 (기간·재료·상태 필터, 페이지네이션)
 * GET    /purchase-orders/summary         — 기간별 발주 합계 + 재료별 분류
 * GET    /purchase-orders/export          — 발주 이력 Excel 다운로드
 */
@Slf4j
@RestController
@RequestMapping("/purchase-orders")
@RequiredArgsConstructor
public class PurchaseOrderController {

    private final PurchaseOrderService purchaseOrderService;
    private final PurchaseOrderExcelService purchaseOrderExcelService;

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

    /**
     * GET /purchase-orders/export?from=2026-04-01&to=2026-05-31
     * 발주 이력 Excel 파일 다운로드.
     */
    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @AuthenticationPrincipal CustomUserDetails principal,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        LocalDate end = to != null ? to : LocalDate.now();
        LocalDate start = from != null ? from : end.minusDays(30);

        try {
            byte[] excel = purchaseOrderExcelService.generateExcel(principal.getId(), start, end);

            String filename = "발주이력_"
                    + start.format(DateTimeFormatter.BASIC_ISO_DATE)
                    + "~"
                    + end.format(DateTimeFormatter.BASIC_ISO_DATE)
                    + ".xlsx";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
            headers.setContentDisposition(
                    ContentDisposition.attachment()
                            .filename(filename, StandardCharsets.UTF_8)
                            .build());

            return ResponseEntity.ok().headers(headers).body(excel);

        } catch (IOException e) {
            log.error("[PURCHASE-EXCEL] 발주서 생성 실패 userId={}", principal.getId(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
