package com.naengjang_goat.inventory_system.menu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.naengjang_goat.inventory_system.global.security.CustomUserDetails;
import com.naengjang_goat.inventory_system.global.service.FcmService;
import com.naengjang_goat.inventory_system.inventory.domain.Ingredient;
import com.naengjang_goat.inventory_system.inventory.domain.InventoryBatch;
import com.naengjang_goat.inventory_system.inventory.repository.IngredientRepository;
import com.naengjang_goat.inventory_system.inventory.repository.InventoryBatchRepository;
import com.naengjang_goat.inventory_system.menu.domain.Menu;
import com.naengjang_goat.inventory_system.menu.repository.MenuRepository;
import com.naengjang_goat.inventory_system.user.domain.Role;
import com.naengjang_goat.inventory_system.user.domain.User;
import com.naengjang_goat.inventory_system.user.repository.UserRepository;
import com.naengjang_goat.inventory_system.user.dto.OnboardRequest;
import com.naengjang_goat.inventory_system.user.service.OnboardService;
import com.naengjang_goat.inventory_system.workflow.PosReportService;
import com.naengjang_goat.inventory_system.workflow.PosUploadService;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = {"spring.jpa.show-sql=false", "logging.level.root=WARN"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MenuIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired UserRepository users;
  @Autowired MenuRepository menus;
  @Autowired IngredientRepository ingredients;
  @Autowired InventoryBatchRepository batches;
  @Autowired PosUploadService pos;
  @Autowired PosReportService reports;
  @Autowired Clock clock;
  @Autowired OnboardService onboard;
  @Autowired TransactionTemplate tx;
  @Autowired jakarta.persistence.EntityManager em;
  @MockitoBean FcmService fcm;
  private User owner;
  private User other;
  private Ingredient cabbage;
  private Ingredient otherIngredient;
  private Menu emptyMenu;
  private Menu otherMenu;
  private CustomUserDetails principal;
  private LocalDate today;

  @BeforeEach
  void setup() {
    when(fcm.deliver(nullable(String.class), anyString(), anyString(), anyMap()))
        .thenReturn("SENT");
    today = LocalDate.now(clock);
    owner = users.save(new User("menu_owner_" + UUID.randomUUID(), "test", "점주", Role.OWNER));
    other = users.save(new User("menu_other_" + UUID.randomUUID(), "test", "다른점주", Role.OWNER));
    principal = new CustomUserDetails(owner.getId(), owner.getUsername(), "test",
        List.of(new SimpleGrantedAuthority("ROLE_OWNER")));
    cabbage = ingredients.save(new Ingredient(owner, "배추", "g", new BigDecimal("1000")));
    otherIngredient = ingredients.save(new Ingredient(other, "배추", "g", BigDecimal.ZERO));
    batches.save(new InventoryBatch(cabbage, new BigDecimal("10000"), BigDecimal.ONE,
        today.minusDays(5), today.plusDays(10)));
    batches.save(new InventoryBatch(otherIngredient, new BigDecimal("200"), BigDecimal.ONE,
        today, today.plusDays(10)));
    emptyMenu = menus.save(new Menu(owner, "빈 메뉴", 0));
    otherMenu = menus.save(new Menu(other, "다른점주 메뉴", 0));
  }

  @AfterEach
  void cleanup() {
    tx.execute(status -> {
      for (Long id : List.of(owner.getId(), other.getId())) {
        for (String entity : List.of("ClosingNotification", "PosUpload", "PosMenuMapping"))
          em.createQuery("delete from " + entity + " e where e.userId = :id")
              .setParameter("id", id).executeUpdate();
        em.createQuery("delete from InventoryBatch b where b.ingredient.user.id = :id")
            .setParameter("id", id).executeUpdate();
        menus.deleteAll(menus.findAllByUserIdWithBom(id));
        em.flush();
        em.createQuery("delete from Ingredient i where i.user.id = :id")
            .setParameter("id", id).executeUpdate();
        users.deleteById(id);
      }
      return null;
    });
  }

  private Map<String, Object> item(Long id, String quantity, String unit) {
    return Map.of("ingredientId", id, "requiredQuantity", new BigDecimal(quantity), "unit", unit);
  }

  private String body(String name, int price, List<Map<String, Object>> recipe) throws Exception {
    return json.writeValueAsString(Map.of("name", name, "price", price, "recipe", recipe));
  }

  private JsonNode create(String name, String quantity, String unit) throws Exception {
    var result = mvc.perform(post("/menus").with(user(principal)).contentType("application/json")
            .content(body(name, 8000, List.of(item(cabbage.getId(), quantity, unit)))))
        .andExpect(status().isOk()).andReturn();
    return json.readTree(result.getResponse().getContentAsString());
  }

  private void update(long id, String body, int statusCode) throws Exception {
    mvc.perform(put("/menus/" + id).with(user(principal)).contentType("application/json").content(body))
        .andExpect(status().is(statusCode));
  }

  private JsonNode list() throws Exception {
    return json.readTree(mvc.perform(get("/menus").with(user(principal)))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
  }

  private JsonNode listed(long id) throws Exception {
    for (var menu : list()) if (menu.path("menuId").asLong() == id) return menu;
    throw new AssertionError("menu not found in list");
  }

  private MockMultipartFile workbook(LocalDate date, int quantity) throws Exception {
    try (var wb = new XSSFWorkbook(); var bytes = new ByteArrayOutputStream()) {
      var sheet = wb.createSheet("판매내역");
      var header = sheet.createRow(0);
      String[] columns = {"영업일", "메뉴코드", "메뉴명", "판매수량", "판매금액"};
      for (int i = 0; i < columns.length; i++) header.createCell(i).setCellValue(columns[i]);
      var row = sheet.createRow(1);
      row.createCell(0).setCellValue(date.toString());
      row.createCell(1).setCellValue("SOUP001");
      row.createCell(2).setCellValue("배추국");
      row.createCell(3).setCellValue(quantity);
      row.createCell(4).setCellValue(quantity * 8000);
      wb.write(bytes);
      return new MockMultipartFile("file", "sales.xlsx",
          "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes.toByteArray());
    }
  }

  @Test
  void createNormalizesRecipeAndListIncludesExistingEmptyMenus() throws Exception {
    var menu = create(" 배추국 ", "0.5", "kg");
    assertThat(menu.path("name").asText()).isEqualTo("배추국");
    assertThat(menu.path("recipe").get(0).path("requiredQuantity").decimalValue())
        .isEqualByComparingTo("500");
    assertThat(menu.path("recipe").get(0).path("unit").asText()).isEqualTo("g");
    assertThat(menu.path("recipe").get(0).path("ingredientName").asText()).isEqualTo("배추");
    assertThat(listed(emptyMenu.getId()).path("recipe").size()).isZero();
    for (var row : list()) assertThat(row.path("menuId").asLong()).isNotEqualTo(otherMenu.getId());
  }

  @Test
  void editedRecipeAffectsNextUploadWithoutChangingHistoricalReport() throws Exception {
    long id = create("배추국", "0.5", "kg").path("menuId").asLong();
    pos.map(owner.getId(), "SOUP001", id);
    var first = pos.upload(owner.getId(), workbook(today.minusDays(1), 2));
    assertThat(first.ingredientConsumption().getFirst().quantity()).isEqualByComparingTo("1000");
    update(id, body("배추국", 9000, List.of(item(cabbage.getId(), "250", "g"))), 200);
    var second = pos.upload(owner.getId(), workbook(today, 2));
    assertThat(second.ingredientConsumption().getFirst().quantity()).isEqualByComparingTo("500");
    var historical = reports.daily(owner.getId(), today.minusDays(1));
    assertThat(historical.ingredientConsumption().getFirst().quantity()).isEqualByComparingTo("1000");
    assertThat(historical.salesAmount()).isEqualByComparingTo("16000");
    assertThat(batches.sumQuantityByIngredientId(cabbage.getId())).isEqualByComparingTo("8500");
  }

  @Test
  void duplicateNameIsRejectedForBothCreateAndEdit() throws Exception {
    long id = create("배추국", "500", "g").path("menuId").asLong();
    mvc.perform(post("/menus").with(user(principal)).contentType("application/json")
            .content(body(" 배추국 ", 8000, List.of(item(cabbage.getId(), "100", "g")))))
        .andExpect(status().isConflict());
    update(id, body(emptyMenu.getName(), 9000, List.of(item(cabbage.getId(), "100", "g"))), 409);
    assertThat(listed(id).path("name").asText()).isEqualTo("배추국");
    assertThat(listed(id).path("recipe").get(0).path("requiredQuantity").decimalValue())
        .isEqualByComparingTo("500");
  }

  @Test
  void wrongUnitDuplicateIngredientsAndOverflowKeepExistingRecipe() throws Exception {
    long id = create("배추국", "500", "g").path("menuId").asLong();
    for (var recipe : List.of(List.of(item(cabbage.getId(), "100", "ml")),
        List.of(item(cabbage.getId(), "100", "g"), item(cabbage.getId(), "200", "g")),
        List.of(item(cabbage.getId(), "9999999.999", "kg")))) {
      update(id, body("수정실패 메뉴", 1234, recipe), 400);
      var unchanged = listed(id);
      assertThat(unchanged.path("name").asText()).isEqualTo("배추국");
      assertThat(unchanged.path("price").asInt()).isEqualTo(8000);
      assertThat(unchanged.path("recipe").get(0).path("requiredQuantity").decimalValue())
          .isEqualByComparingTo("500");
    }
  }

  @Test
  void cannotUseOtherOwnersIngredientOrEditOtherOwnersMenu() throws Exception {
    mvc.perform(post("/menus").with(user(principal)).contentType("application/json")
            .content(body("타인재료", 0, List.of(item(otherIngredient.getId(), "1", "g")))))
        .andExpect(status().isForbidden());
    update(otherMenu.getId(), body("침범", 0, List.of(item(cabbage.getId(), "1", "g"))), 404);
    assertThat(menus.findById(otherMenu.getId()).orElseThrow().getName()).isEqualTo("다른점주 메뉴");
    assertThat(list().size()).isEqualTo(1);
  }

  @Test
  void batchLookupOnlyReturnsOwnersIngredients() throws Exception {
    mvc.perform(get("/ingredients/" + cabbage.getId() + "/batches").with(user(principal)))
        .andExpect(status().isOk()).andExpect(jsonPath("$[0].quantity").value(10000));
    mvc.perform(get("/ingredients/" + otherIngredient.getId() + "/batches").with(user(principal)))
        .andExpect(status().isNotFound());
    mvc.perform(get("/ingredients/999999999/batches").with(user(principal)))
        .andExpect(status().isNotFound());
  }

  @Test
  void invalidPriceQuantityAndEmptyRecipeAreRejected() throws Exception {
    for (String invalid : List.of(body("음수 가격", -1, List.of(item(cabbage.getId(), "1", "g"))),
        body("영 소모량", 0, List.of(item(cabbage.getId(), "0", "g"))),
        body("정밀도 초과", 0, List.of(item(cabbage.getId(), "0.0001", "g"))),
        body("빈 레시피", 0, List.of()), body(" ", 0, List.of(item(cabbage.getId(), "1", "g"))))) {
      mvc.perform(post("/menus").with(user(principal)).contentType("application/json").content(invalid))
          .andExpect(status().isBadRequest());
    }
    assertThat(list().size()).isEqualTo(1);
  }

  @Test
  void repeatedOnboardingPreservesEditedRecipeAndDoesNotDuplicateMenus() throws Exception {
    String category = "TEST_" + UUID.randomUUID().toString().substring(0, 8);
    Long templateId = tx.execute(status -> {
      em.createNativeQuery("insert into recipe_template(category,menu_name,source,source_recipe_idx) "
              + "values (:category,'템플릿 배추국','menu-test',:idx)")
          .setParameter("category", category).setParameter("idx", owner.getId().intValue())
          .executeUpdate();
      Long id = ((Number) em.createNativeQuery("select id from recipe_template "
              + "where source='menu-test' and source_recipe_idx=:idx")
          .setParameter("idx", owner.getId().intValue()).getSingleResult()).longValue();
      em.createNativeQuery("insert into recipe_template_bom(template_id,ingredient_name,quantity,unit,is_discount) "
              + "values (:id,'배추',2000,'g',0)")
          .setParameter("id", id).executeUpdate();
      return id;
    });
    try {
      var initial = onboard.onboard(owner.getId(), new OnboardRequest(List.of(category)));
      assertThat(initial.createdMenus()).isEqualTo(1);
      assertThat(initial.createdBom()).isEqualTo(1);
      long menuId = 0;
      for (var menu : list())
        if (menu.path("name").asText().equals("템플릿 배추국")) menuId = menu.path("menuId").asLong();
      assertThat(menuId).isPositive();
      update(menuId, body("템플릿 배추국", 9500, List.of(item(cabbage.getId(), "375", "g"))), 200);
      var repeated = onboard.onboard(owner.getId(), new OnboardRequest(List.of(category)));
      assertThat(repeated.createdMenus()).isZero();
      assertThat(repeated.createdBom()).isZero();
      assertThat(list().size()).isEqualTo(2);
      var unchanged = listed(menuId);
      assertThat(unchanged.path("price").asInt()).isEqualTo(9500);
      assertThat(unchanged.path("recipe").get(0).path("requiredQuantity").decimalValue())
          .isEqualByComparingTo("375");
    } finally {
      tx.execute(status -> {
        em.createNativeQuery("delete from recipe_template where id=:id")
            .setParameter("id", templateId).executeUpdate();
        return null;
      });
    }
  }
}
