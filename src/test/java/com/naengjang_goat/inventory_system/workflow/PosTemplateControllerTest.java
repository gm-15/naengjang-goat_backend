package com.naengjang_goat.inventory_system.workflow;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.naengjang_goat.inventory_system.global.security.CustomUserDetails;
import com.naengjang_goat.inventory_system.menu.domain.Menu;
import com.naengjang_goat.inventory_system.menu.repository.MenuRepository;
import java.io.ByteArrayInputStream;
import java.util.List;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

class PosTemplateControllerTest {
  @Test
  void templateContainsOnlyHeadersAndOwnedMenuReferenceWithTextCodes() throws Exception {
    var menus = mock(MenuRepository.class);
    var mappings = mock(PosMenuMappingRepository.class);
    var user = mock(CustomUserDetails.class);
    when(user.getId()).thenReturn(7L);
    var menu = new Menu(null, "우리 메뉴", 8000);
    menu.setId(3L);
    when(menus.findAllByUserIdAndNameContaining(7L, "")).thenReturn(List.of(menu));
    var ownedMapping = new PosMenuMapping();
    ownedMapping.setMenuId(3L);
    ownedMapping.setPosCode("001");
    var unownedMenuMapping = new PosMenuMapping();
    unownedMenuMapping.setMenuId(99L);
    unownedMenuMapping.setPosCode("다른 매장 코드");
    when(mappings.findAllByUserId(7L)).thenReturn(List.of(ownedMapping, unownedMenuMapping));

    var response = new PosTemplateController(menus, mappings).template(user);
    assertEquals("no-store", response.getHeaders().getFirst("Cache-Control"));
    try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(response.getBody()))) {
      var sales = workbook.getSheet("판매내역");
      var headers = List.of("영업일", "메뉴코드", "메뉴명", "판매수량", "판매금액");
      for (int index = 0; index < headers.size(); index++)
        assertEquals(headers.get(index), sales.getRow(0).getCell(index).getStringCellValue());
      assertEquals(1, sales.getPhysicalNumberOfRows());
      var reference = workbook.getSheet("등록메뉴");
      assertEquals(2, reference.getPhysicalNumberOfRows());
      assertEquals("우리 메뉴", reference.getRow(1).getCell(0).getStringCellValue());
      assertEquals("001", reference.getRow(1).getCell(1).getStringCellValue());
      assertEquals(CellType.STRING, reference.getRow(1).getCell(1).getCellType());
      assertEquals(8000, reference.getRow(1).getCell(2).getNumericCellValue());
      assertNotNull(workbook.getSheet("작성안내"));
    }
    verify(menus).findAllByUserIdAndNameContaining(7L, "");
    verify(mappings).findAllByUserId(7L);
  }

  @Test
  void unchangedBlankTemplateCannotBeImportedAsSales() throws Exception {
    var menus = mock(MenuRepository.class);
    var mappings = mock(PosMenuMappingRepository.class);
    var user = mock(CustomUserDetails.class);
    when(user.getId()).thenReturn(7L);
    when(menus.findAllByUserIdAndNameContaining(7L, "")).thenReturn(List.of());
    when(mappings.findAllByUserId(7L)).thenReturn(List.of());
    byte[] content = new PosTemplateController(menus, mappings).template(user).getBody();
    var file = new MockMultipartFile("file", "pos-sales-template.xlsx",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", content);
    var exception = assertThrows(ResponseStatusException.class, () -> new PosExcelReader().read(file));
    assertEquals("판매 내역이 없습니다", exception.getReason());
  }
}
