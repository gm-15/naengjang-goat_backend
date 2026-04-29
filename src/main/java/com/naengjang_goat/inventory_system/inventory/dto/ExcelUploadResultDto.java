package com.naengjang_goat.inventory_system.inventory.dto;

import java.util.List;

/**
 * 재고 일괄 업로드 결과 DTO.
 *
 * totalRows  : 헤더 제외 전체 데이터 행 수
 * successCount: 정상 입력된 배치 수
 * failCount  : 오류로 스킵된 행 수
 * errors     : 오류 행 설명 목록 (예: "3행: 수량 형식 오류")
 */
public record ExcelUploadResultDto(
        int totalRows,
        int successCount,
        int failCount,
        List<String> errors
) {}
