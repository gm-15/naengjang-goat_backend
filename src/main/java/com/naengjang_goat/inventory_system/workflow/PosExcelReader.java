package com.naengjang_goat.inventory_system.workflow;

import java.io.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Component
public class PosExcelReader {
  private static final List<String> HEADERS = List.of("영업일", "메뉴코드", "메뉴명", "판매수량", "판매금액");

  public record RowData(
      int row, LocalDate date, String code, String name, int quantity, BigDecimal amount) {}

  public List<RowData> read(MultipartFile file) {
    if (file.isEmpty()
        || file.getSize() > 5_000_000
        || file.getOriginalFilename() == null
        || !file.getOriginalFilename().toLowerCase(Locale.ROOT).endsWith(".xlsx"))
      throw bad("5MB 이하 .xlsx 파일을 올려주세요");
    try (var workbook = new XSSFWorkbook(file.getInputStream())) {
      Sheet sheet = workbook.getSheet("판매내역");
      if (sheet == null || sheet.getRow(0) == null) throw bad("판매내역 시트와 헤더가 필요합니다");
      for (int i = 0; i < HEADERS.size(); i++)
        if (!HEADERS.get(i).equals(text(sheet.getRow(0).getCell(i))))
          throw bad("헤더: " + String.join(", ", HEADERS));
      if (sheet.getLastRowNum() > 1000) throw bad("최대 1000행까지 지원합니다");
      List<RowData> rows = new ArrayList<>();
      LocalDate businessDate = null;
      for (int i = 1; i <= sheet.getLastRowNum(); i++) {
        Row row = sheet.getRow(i);
        if (row == null) continue;
        boolean blank = true;
        for (int c = 0; c < 5; c++) if (!text(row.getCell(c)).isBlank()) blank = false;
        if (blank) continue;
        try {
          Cell dateCell = row.getCell(0);
          LocalDate date =
              dateCell != null
                      && dateCell.getCellType() == CellType.NUMERIC
                      && DateUtil.isCellDateFormatted(dateCell)
                  ? dateCell.getLocalDateTimeCellValue().toLocalDate()
                  : LocalDate.parse(text(dateCell));
          if (businessDate != null && !businessDate.equals(date)) throw bad("파일 하나에 영업일 하나만 포함하세요");
          businessDate = date;
          String code = text(row.getCell(1));
          String name = text(row.getCell(2));
          if (code.length() > 100 || name.isBlank() || name.length() > 255)
            throw bad("메뉴코드/메뉴명을 확인하세요");
          int quantity = number(row.getCell(3)).intValueExact();
          BigDecimal amount = number(row.getCell(4));
          if (quantity <= 0
              || quantity > 100000
              || amount.signum() < 0
              || amount.scale() > 2
              || amount.precision() - amount.scale() > 10)
            throw bad("수량은 양의 정수, 금액은 0 이상(소수 최대 2자리)이어야 합니다");
          rows.add(new RowData(i + 1, date, code, name, quantity, amount));
        } catch (Exception e) {
          throw bad(
              (i + 1)
                  + "행: "
                  + (e instanceof ResponseStatusException x
                      ? x.getReason()
                      : "날짜/수량/금액 형식을 확인하세요"));
        }
      }
      if (rows.isEmpty()) throw bad("판매 내역이 없습니다");
      return rows;
    } catch (ResponseStatusException e) {
      throw e;
    } catch (Exception e) {
      throw bad("읽을 수 없는 엑셀 파일입니다");
    }
  }

  private String text(Cell cell) {
    if (cell == null) return "";
    if (cell.getCellType() == CellType.FORMULA || cell.getCellType() == CellType.ERROR)
      throw bad("수식/오류 셀은 지원하지 않습니다. 값으로 저장하세요");
    return new DataFormatter(Locale.ROOT).formatCellValue(cell).trim();
  }

  private BigDecimal number(Cell cell) {
    if (cell == null) throw bad("수량/금액이 없습니다");
    if (cell.getCellType() == CellType.FORMULA) throw bad("수식은 지원하지 않습니다");
    return cell.getCellType() == CellType.NUMERIC
        ? BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros()
        : new BigDecimal(text(cell).replace(",", "")).stripTrailingZeros();
  }

  private ResponseStatusException bad(String message) {
    return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
  }
}
