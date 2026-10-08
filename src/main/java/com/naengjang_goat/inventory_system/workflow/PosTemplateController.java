package com.naengjang_goat.inventory_system.workflow;

import com.naengjang_goat.inventory_system.global.security.CustomUserDetails;
import com.naengjang_goat.inventory_system.menu.domain.Menu;
import com.naengjang_goat.inventory_system.menu.repository.MenuRepository;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Comparator;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 실제 판매 데이터를 입력하는 빈 양식. 시연용 가상 판매를 포함하지 않는다. */
@RestController
@RequiredArgsConstructor
public class PosTemplateController {
  private final MenuRepository menus;
  private final PosMenuMappingRepository mappings;

  @GetMapping("/pos/template")
  @Transactional(readOnly = true)
  public ResponseEntity<byte[]> template(@AuthenticationPrincipal CustomUserDetails user)
      throws IOException {
    try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
      CellStyle textStyle = workbook.createCellStyle();
      textStyle.setDataFormat(workbook.createDataFormat().getFormat("@"));
      CellStyle headerStyle = workbook.createCellStyle();
      var headerFont = workbook.createFont();
      headerFont.setBold(true);
      headerStyle.setFont(headerFont);

      Sheet sales = workbook.createSheet("판매내역");
      header(sales, headerStyle, "영업일", "메뉴코드", "메뉴명", "판매수량", "판매금액");
      for (int column = 0; column < 3; column++) sales.setDefaultColumnStyle(column, textStyle);
      // 헤더 외 판매 행은 만들지 않는다. 작성 전 업로드하면 '판매 내역 없음'으로 거절된다.

      Sheet guide = workbook.createSheet("작성안내");
      header(guide, headerStyle, "작성 방법");
      String[] instructions = {
        "판매내역 시트의 2행부터 실제 POS 판매 자료를 작성하세요. 이 빈 양식은 그대로 업로드할 수 없습니다.",
        "헤더와 순서를 유지하세요: 영업일, 메뉴코드, 메뉴명, 판매수량, 판매금액.",
        "파일 하나에 하루 전체 판매를 담으세요. 영업일은 YYYY-MM-DD 형식이며 미래 날짜는 사용할 수 없습니다.",
        "같은 메뉴의 하루 판매를 합산하여 한 행으로 작성하세요. 판매수량은 양의 정수이고, 판매금액은 해당 메뉴의 하루 총 판매금액입니다.",
        "메뉴명은 등록된 메뉴명과 정확히 같아야 하고, 해당 메뉴에 재료 레시피가 등록되어 있어야 합니다.",
        "POS 메뉴코드를 사용하면 앱에서 해당 코드를 메뉴에 먼저 연결하세요. 코드를 비워 두려면 중복되지 않는 등록 메뉴명을 사용하세요.",
        "등록메뉴 시트에서 내 메뉴명과 연결된 POS 코드를 확인하세요. 코드가 없는 메뉴는 연결되지 않은 상태입니다.",
        "수식 대신 값으로 저장하세요. .xlsx 파일 5MB 이하, 판매 행 최대 1000개, 메뉴별 판매수량 최대 100000개를 지원합니다.",
        "판매금액은 0 이상의 숫자이며 소수 둘째 자리까지 지원합니다. 취소·환불 처리는 이번 버전 범위에 포함되지 않습니다.",
        "업로드하면 판매와 재고 차감이 즉시 반영됩니다. 같은 영업일의 다른 파일을 다시 반영할 수 없으니 하루 전체 자료를 확인하세요."
      };
      CellStyle wrap = workbook.createCellStyle();
      wrap.setWrapText(true);
      guide.setColumnWidth(0, 25000);
      for (int index = 0; index < instructions.length; index++) {
        var row = guide.createRow(index + 1);
        row.setHeightInPoints(34);
        var cell = row.createCell(0);
        cell.setCellValue(instructions[index]);
        cell.setCellStyle(wrap);
      }

      Sheet registered = workbook.createSheet("등록메뉴");
      header(registered, headerStyle, "메뉴명", "연결된 POS 메뉴코드", "메뉴 판매가(원)");
      registered.setDefaultColumnStyle(0, textStyle);
      registered.setDefaultColumnStyle(1, textStyle);
      var ownedMenus = new java.util.ArrayList<>(menus.findAllByUserIdAndNameContaining(user.getId(), ""));
      var ownedMappings = mappings.findAllByUserId(user.getId());
      ownedMenus.sort(Comparator.comparing(Menu::getName).thenComparing(Menu::getId));
      int rowIndex = 1;
      for (var menu : ownedMenus) {
        var codes = ownedMappings.stream()
            .filter(mapping -> mapping.getMenuId().equals(menu.getId()))
            .map(PosMenuMapping::getPosCode).sorted().toList();
        if (codes.isEmpty()) codes = java.util.List.of("");
        for (String code : codes) {
          var row = registered.createRow(rowIndex++);
          row.createCell(0).setCellValue(menu.getName());
          row.createCell(1).setCellValue(code);
          row.createCell(2).setCellValue(menu.getPrice());
        }
      }

      workbook.write(output);
      return ResponseEntity.ok()
          .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
          .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=pos-sales-template.xlsx")
          .header(HttpHeaders.CACHE_CONTROL, "no-store")
          .body(output.toByteArray());
    }
  }

  private void header(Sheet sheet, CellStyle style, String... labels) {
    var row = sheet.createRow(0);
    for (int column = 0; column < labels.length; column++) {
      var cell = row.createCell(column);
      cell.setCellValue(labels[column]);
      cell.setCellStyle(style);
      sheet.setColumnWidth(column, 6000);
    }
    sheet.createFreezePane(0, 1);
  }
}
