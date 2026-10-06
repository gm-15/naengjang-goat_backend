package com.naengjang_goat.inventory_system.workflow;

import com.naengjang_goat.inventory_system.global.security.CustomUserDetails;
import java.io.*;
import java.time.*;
import lombok.RequiredArgsConstructor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@Profile("demo")
@RequiredArgsConstructor
public class DemoDataController {
  private final DemoDataService demo;
  private final Clock clock;

  @PostMapping("/demo/setup")
  public DemoDataService.SetupResult setup(@AuthenticationPrincipal CustomUserDetails user) {
    return demo.setup(user.getId());
  }

  @GetMapping("/demo/pos-sample")
  public ResponseEntity<byte[]> sample(@AuthenticationPrincipal CustomUserDetails user)
      throws IOException {
    if (!"demo".equals(user.getUsername()))
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "demo 계정 전용입니다");
    try (var wb = new XSSFWorkbook();
        var out = new ByteArrayOutputStream()) {
      var sheet = wb.createSheet("판매내역");
      var header = sheet.createRow(0);
      String[] headers = {"영업일", "메뉴코드", "메뉴명", "판매수량", "판매금액"};
      for (int i = 0; i < headers.length; i++) {
        header.createCell(i).setCellValue(headers[i]);
        sheet.setColumnWidth(i, 5000);
      }
      var row = sheet.createRow(1);
      row.createCell(0).setCellValue(LocalDate.now(clock).toString());
      row.createCell(1).setCellValue("DEMO001");
      row.createCell(2).setCellValue("배추국");
      row.createCell(3).setCellValue(8);
      row.createCell(4).setCellValue(64000);
      wb.write(out);
      return ResponseEntity.ok()
          .contentType(
              MediaType.parseMediaType(
                  "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
          .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=pos-demo.xlsx")
          .body(out.toByteArray());
    }
  }
}
