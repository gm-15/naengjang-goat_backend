package com.naengjang_goat.inventory_system.purchase.service;

import com.naengjang_goat.inventory_system.purchase.domain.PurchaseOrder;
import com.naengjang_goat.inventory_system.purchase.repository.PurchaseOrderRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 발주 이력 → Excel 파일 생성.
 * Apache POI (poi-ooxml) 사용.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PurchaseOrderExcelService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final String[] HEADERS =
            {"발주일", "재료명", "거래처", "수량", "단위", "단가(원)", "합계(원)", "상태", "메모"};

    private final PurchaseOrderRepository purchaseOrderRepository;

    /**
     * 기간 내 발주 이력을 Excel 바이트 배열로 반환.
     * 파일명 형식: 발주이력_yyyyMMdd~yyyyMMdd.xlsx
     */
    public byte[] generateExcel(Long userId, LocalDate from, LocalDate to) throws IOException {
        List<PurchaseOrder> orders = purchaseOrderRepository.findForSummary(userId, from, to);

        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = wb.createSheet("발주이력");
            sheet.setDefaultColumnWidth(15);

            // ── 제목 행 ──────────────────────────────────────────
            CellStyle titleStyle = createTitleStyle(wb);
            Row titleRow = sheet.createRow(0);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue("발주 이력 (" + from.format(DATE_FMT) + " ~ " + to.format(DATE_FMT) + ")");
            titleCell.setCellStyle(titleStyle);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, HEADERS.length - 1));

            // ── 헤더 행 ──────────────────────────────────────────
            CellStyle headerStyle = createHeaderStyle(wb);
            Row headerRow = sheet.createRow(1);
            for (int i = 0; i < HEADERS.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(HEADERS[i]);
                cell.setCellStyle(headerStyle);
            }

            // ── 데이터 행 ─────────────────────────────────────────
            CellStyle dataStyle = createDataStyle(wb);
            CellStyle numberStyle = createNumberStyle(wb);
            int rowNum = 2;
            BigDecimal grandTotal = BigDecimal.ZERO;

            for (PurchaseOrder o : orders) {
                Row row = sheet.createRow(rowNum++);
                setCell(row, 0, o.getOrderedAt().format(DATE_FMT), dataStyle);
                setCell(row, 1, o.getIngredient().getName(), dataStyle);
                setCell(row, 2, o.getSupplier() != null ? o.getSupplier() : "", dataStyle);
                setCellNum(row, 3, o.getQuantity().doubleValue(), numberStyle);
                setCell(row, 4, o.getBaseUnit() != null ? o.getBaseUnit() : "", dataStyle);
                setCellNum(row, 5, o.getUnitPrice().doubleValue(), numberStyle);
                setCellNum(row, 6, o.getTotalAmount().doubleValue(), numberStyle);
                setCell(row, 7, o.getStatus().name(), dataStyle);
                setCell(row, 8, o.getMemo() != null ? o.getMemo() : "", dataStyle);
                grandTotal = grandTotal.add(o.getTotalAmount());
            }

            // ── 합계 행 ──────────────────────────────────────────
            CellStyle totalStyle = createTotalStyle(wb);
            Row totalRow = sheet.createRow(rowNum);
            Cell totalLabelCell = totalRow.createCell(0);
            totalLabelCell.setCellValue("합계");
            totalLabelCell.setCellStyle(totalStyle);
            sheet.addMergedRegion(new CellRangeAddress(rowNum, rowNum, 0, 5));
            Cell totalAmountCell = totalRow.createCell(6);
            totalAmountCell.setCellValue(grandTotal.doubleValue());
            totalAmountCell.setCellStyle(totalStyle);

            wb.write(out);
            return out.toByteArray();
        }
    }

    private void setCell(Row row, int col, String value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    private void setCellNum(Row row, int col, double value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    private CellStyle createTitleStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 14);
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }

    private CellStyle createHeaderStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        setBorder(style);
        return style;
    }

    private CellStyle createDataStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        setBorder(style);
        return style;
    }

    private CellStyle createNumberStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        setBorder(style);
        style.setAlignment(HorizontalAlignment.RIGHT);
        DataFormat fmt = wb.createDataFormat();
        style.setDataFormat(fmt.getFormat("#,##0"));
        return style;
    }

    private CellStyle createTotalStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.RIGHT);
        setBorder(style);
        DataFormat fmt = wb.createDataFormat();
        style.setDataFormat(fmt.getFormat("#,##0"));
        return style;
    }

    private void setBorder(CellStyle style) {
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
    }
}
