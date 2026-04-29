package com.naengjang_goat.inventory_system.inventory.service;

import com.naengjang_goat.inventory_system.inventory.domain.Ingredient;
import com.naengjang_goat.inventory_system.inventory.domain.InventoryBatch;
import com.naengjang_goat.inventory_system.inventory.dto.ExcelUploadResultDto;
import com.naengjang_goat.inventory_system.inventory.repository.IngredientRepository;
import com.naengjang_goat.inventory_system.inventory.repository.InventoryBatchRepository;
import com.naengjang_goat.inventory_system.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * 재고 일괄 업로드 서비스 (Excel .xlsx).
 *
 * ── 엑셀 템플릿 형식 ──────────────────────────────────────────
 * 1행 (헤더, 자동 스킵): 재료명 | 수량 | 단위 | 입고일 | 유통기한 | 단가
 *
 * 컬럼 (0-indexed):
 *   0: 재료명       String   (필수) 예: 쌀
 *   1: 수량         Numeric  (필수) 예: 10 또는 10.5
 *   2: 단위         String   (선택) 예: kg, g, 개 — 없으면 기존 재료의 baseUnit 사용, 신규 재료는 'g'
 *   3: 입고일       Date/String (선택) yyyy-MM-dd — 없으면 오늘
 *   4: 유통기한     Date/String (필수) yyyy-MM-dd
 *   5: 단가(원/단위) Numeric  (선택) 없으면 null
 * ─────────────────────────────────────────────────────────────
 *
 * 처리 방식:
 *   - 재료명 + userId 로 기존 재료 조회 → 없으면 자동 신규 생성
 *   - InventoryBatch 레코드 추가 (기존 배치에 합산 X, 별도 배치 생성)
 *   - 행 단위 오류 시 해당 행만 스킵 + 오류 메시지 수집, 나머지는 정상 처리
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryExcelService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final String DEFAULT_BASE_UNIT = "g";

    private final IngredientRepository ingredientRepository;
    private final InventoryBatchRepository batchRepository;
    private final UserRepository userRepository;

    /**
     * Excel 파일을 파싱해 재고 배치를 일괄 입력한다.
     *
     * @param userId 점주 ID
     * @param file   업로드된 .xlsx 파일
     * @return 처리 결과 (성공/실패 건수, 오류 목록)
     */
    @Transactional
    public ExcelUploadResultDto upload(Long userId, MultipartFile file) throws IOException {
        int totalRows = 0;
        int successCount = 0;
        List<String> errors = new ArrayList<>();

        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);

            for (Row row : sheet) {
                // 1행(index 0) = 헤더 스킵
                if (row.getRowNum() == 0) continue;

                // 빈 행 스킵 (재료명 셀이 비어있으면 종료)
                Cell nameCell = row.getCell(0);
                if (nameCell == null || nameCell.getCellType() == CellType.BLANK) break;

                totalRows++;
                int rowNum = row.getRowNum() + 1; // 사용자용 1-based 행 번호

                try {
                    processRow(userId, row, rowNum);
                    successCount++;
                } catch (ExcelRowException e) {
                    errors.add(e.getMessage());
                    log.warn("[ExcelUpload] userId={} {}행 스킵: {}", userId, rowNum, e.getMessage());
                }
            }
        }

        int failCount = totalRows - successCount;
        log.info("[ExcelUpload] userId={} 완료 — 전체:{} 성공:{} 실패:{}",
                userId, totalRows, successCount, failCount);

        return new ExcelUploadResultDto(totalRows, successCount, failCount, errors);
    }

    private void processRow(Long userId, Row row, int rowNum) {
        // ── 1. 재료명 ─────────────────────────────────────────
        String name = readString(row, 0, rowNum, "재료명", true);

        // ── 2. 수량 ──────────────────────────────────────────
        BigDecimal quantity = readBigDecimal(row, 1, rowNum, "수량", true);
        if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ExcelRowException(rowNum + "행: 수량은 0보다 커야 합니다 (입력값: " + quantity + ")");
        }

        // ── 3. 단위 (선택) ────────────────────────────────────
        String unit = readStringOptional(row, 2);

        // ── 4. 입고일 (선택, 기본: 오늘) ──────────────────────
        LocalDate inboundDate = readDateOptional(row, 3, rowNum, "입고일");
        if (inboundDate == null) inboundDate = LocalDate.now();

        // ── 5. 유통기한 (필수) ────────────────────────────────
        LocalDate expirationDate = readDate(row, 4, rowNum, "유통기한");
        if (expirationDate.isBefore(LocalDate.now())) {
            throw new ExcelRowException(rowNum + "행: 유통기한이 오늘보다 이전입니다 (" + expirationDate + ")");
        }

        // ── 6. 단가 (선택) ────────────────────────────────────
        BigDecimal costPerUnit = readBigDecimalOptional(row, 5);

        // ── 재료 조회/생성 ────────────────────────────────────
        Ingredient ingredient = ingredientRepository.findByUserIdAndName(userId, name)
                .orElseGet(() -> createIngredient(userId, name, unit));

        // ── InventoryBatch 생성 ───────────────────────────────
        InventoryBatch batch = new InventoryBatch(ingredient, quantity, costPerUnit, inboundDate, expirationDate);
        batchRepository.save(batch);
    }

    private Ingredient createIngredient(Long userId, String name, String unit) {
        var user = userRepository.getReferenceById(userId);
        String baseUnit = (unit != null && !unit.isBlank()) ? unit : DEFAULT_BASE_UNIT;
        Ingredient ingredient = new Ingredient(user, name, baseUnit, null);
        return ingredientRepository.save(ingredient);
    }

    // ── 셀 읽기 헬퍼 ──────────────────────────────────────────────────────

    private String readString(Row row, int col, int rowNum, String fieldName, boolean required) {
        Cell cell = row.getCell(col);
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            if (required) throw new ExcelRowException(rowNum + "행: " + fieldName + "이(가) 비어 있습니다");
            return null;
        }
        return cell.getStringCellValue().trim();
    }

    private String readStringOptional(Row row, int col) {
        Cell cell = row.getCell(col);
        if (cell == null || cell.getCellType() == CellType.BLANK) return null;
        if (cell.getCellType() == CellType.STRING) return cell.getStringCellValue().trim();
        return null;
    }

    private BigDecimal readBigDecimal(Row row, int col, int rowNum, String fieldName, boolean required) {
        Cell cell = row.getCell(col);
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            if (required) throw new ExcelRowException(rowNum + "행: " + fieldName + "이(가) 비어 있습니다");
            return null;
        }
        try {
            if (cell.getCellType() == CellType.NUMERIC) {
                return BigDecimal.valueOf(cell.getNumericCellValue());
            }
            return new BigDecimal(cell.getStringCellValue().trim());
        } catch (NumberFormatException e) {
            throw new ExcelRowException(rowNum + "행: " + fieldName + " 형식 오류 (숫자가 아님)");
        }
    }

    private BigDecimal readBigDecimalOptional(Row row, int col) {
        Cell cell = row.getCell(col);
        if (cell == null || cell.getCellType() == CellType.BLANK) return null;
        try {
            if (cell.getCellType() == CellType.NUMERIC) return BigDecimal.valueOf(cell.getNumericCellValue());
            if (cell.getCellType() == CellType.STRING) return new BigDecimal(cell.getStringCellValue().trim());
        } catch (NumberFormatException ignored) {}
        return null;
    }

    private LocalDate readDate(Row row, int col, int rowNum, String fieldName) {
        LocalDate date = readDateOptional(row, col, rowNum, fieldName);
        if (date == null) throw new ExcelRowException(rowNum + "행: " + fieldName + "이(가) 비어 있습니다");
        return date;
    }

    private LocalDate readDateOptional(Row row, int col, int rowNum, String fieldName) {
        Cell cell = row.getCell(col);
        if (cell == null || cell.getCellType() == CellType.BLANK) return null;

        // Excel 날짜 셀 (Numeric + 날짜 포맷)
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue().toLocalDate();
        }

        // 문자열 날짜 (yyyy-MM-dd)
        if (cell.getCellType() == CellType.STRING) {
            try {
                return LocalDate.parse(cell.getStringCellValue().trim(), DATE_FORMATTER);
            } catch (DateTimeParseException e) {
                throw new ExcelRowException(rowNum + "행: " + fieldName + " 날짜 형식 오류 (yyyy-MM-dd 형식 필요)");
            }
        }
        return null;
    }

    /** 행 단위 파싱 오류 (해당 행 스킵 후 계속 진행) */
    private static class ExcelRowException extends RuntimeException {
        ExcelRowException(String message) { super(message); }
    }
}
