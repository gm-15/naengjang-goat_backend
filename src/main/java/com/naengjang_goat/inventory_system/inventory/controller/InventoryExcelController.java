package com.naengjang_goat.inventory_system.inventory.controller;

import com.naengjang_goat.inventory_system.global.security.CustomUserDetails;
import com.naengjang_goat.inventory_system.inventory.dto.ExcelUploadResultDto;
import com.naengjang_goat.inventory_system.inventory.service.InventoryExcelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * 재고 일괄 업로드 컨트롤러 (Excel .xlsx).
 *
 * POST /inventory/upload/excel
 *   Header : Authorization: Bearer {token}
 *   Body   : multipart/form-data, field="file" (.xlsx 파일)
 *
 * 응답:
 *   200 OK  → { totalRows, successCount, failCount, errors[] }
 *   400     → 파일 미첨부 또는 .xlsx 아닌 파일
 *   500     → 파싱 불가 오류
 *
 * 엑셀 템플릿 컬럼 (1행=헤더 자동 스킵):
 *   A: 재료명 | B: 수량 | C: 단위 | D: 입고일(yyyy-MM-dd) | E: 유통기한(yyyy-MM-dd) | F: 단가
 */
@Slf4j
@RestController
@RequestMapping("/inventory")
@RequiredArgsConstructor
public class InventoryExcelController {

    private final InventoryExcelService excelService;

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
