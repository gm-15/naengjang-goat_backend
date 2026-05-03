package com.naengjang_goat.inventory_system.inventory.controller;

import com.naengjang_goat.inventory_system.global.security.CustomUserDetails;
import com.naengjang_goat.inventory_system.inventory.domain.InventoryBatch;
import com.naengjang_goat.inventory_system.inventory.dto.BatchRequest;
import com.naengjang_goat.inventory_system.inventory.dto.BatchResponse;
import com.naengjang_goat.inventory_system.inventory.dto.ExcelUploadResultDto;
import com.naengjang_goat.inventory_system.inventory.service.InventoryBatchService;
import com.naengjang_goat.inventory_system.inventory.service.InventoryExcelService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * 재고 입력 컨트롤러 (단건 JSON + Excel 일괄).
 *
 * POST /inventory/batches         — 단건 재고 배치 입력 (JSON)
 * POST /inventory/upload/excel    — 일괄 입고 (Excel .xlsx)
 *
 * 인증: JWT Bearer Token
 */
@Slf4j
@RestController
@RequestMapping("/inventory")
@RequiredArgsConstructor
public class InventoryExcelController {

    private final InventoryExcelService excelService;
    private final InventoryBatchService batchService;

    /**
     * POST /inventory/batches
     * 재고 배치 단건 입력.
     *
     * Request:  { "ingredientId": 1, "quantity": 10.0, "costPerUnit": 2500,
     *             "inboundDate": "2026-05-03", "expirationDate": "2026-05-10" }
     * Response: 201 Created — { batchId, quantity, expiresAt }
     */
    @PostMapping("/batches")
    public ResponseEntity<BatchResponse> addBatch(
            @AuthenticationPrincipal CustomUserDetails principal,
            @Valid @RequestBody BatchRequest request) {

        InventoryBatch batch = batchService.create(principal.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(BatchResponse.from(batch));
    }

    @PostMapping("/upload/excel")
    public ResponseEntity<ExcelUploadResultDto> uploadExcel(
            @AuthenticationPrincipal CustomUserDetails principal,
            @RequestParam("file") MultipartFile file) {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".xlsx")) {
            log.warn("[ExcelUpload] 지원하지 않는 파일 형식: {}", filename);
            return ResponseEntity.badRequest().build();
        }

        try {
            ExcelUploadResultDto result = excelService.upload(principal.getId(), file);
            return ResponseEntity.ok(result);
        } catch (IOException e) {
            log.error("[ExcelUpload] 파일 파싱 실패 userId={}: {}", principal.getId(), e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
