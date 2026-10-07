package com.naengjang_goat.inventory_system.workflow;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.naengjang_goat.inventory_system.global.security.CustomUserDetails;
import com.naengjang_goat.inventory_system.global.service.FcmService;
import com.naengjang_goat.inventory_system.inventory.domain.*;
import com.naengjang_goat.inventory_system.inventory.repository.*;
import com.naengjang_goat.inventory_system.menu.domain.*;
import com.naengjang_goat.inventory_system.menu.repository.MenuRepository;
import com.naengjang_goat.inventory_system.settings.domain.*;
import com.naengjang_goat.inventory_system.settings.repository.StoreSettingsRepository;
import com.naengjang_goat.inventory_system.user.domain.*;
import com.naengjang_goat.inventory_system.user.repository.UserRepository;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest(properties = {"spring.jpa.show-sql=false", "logging.level.root=WARN"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(WorkflowIntegrationTest.TimeConfig.class)
class WorkflowIntegrationTest {
  @TestConfiguration
  static class TimeConfig {
    @Bean
    @Primary
    MutableClock testClock() {
      return new MutableClock();
    }
  }

  static class MutableClock extends Clock {
    volatile Instant now = Instant.parse("2026-10-07T06:00:00Z");

    @Override
    public ZoneId getZone() {
      return ZoneId.of("Asia/Seoul");
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return Clock.fixed(now, zone);
    }

    @Override
    public Instant instant() {
      return now;
    }
  }

  @Autowired MutableClock clock;
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired TransactionTemplate tx;
  @Autowired jakarta.persistence.EntityManager em;
  @Autowired UserRepository users;
  @Autowired IngredientRepository ingredients;
  @Autowired InventoryBatchRepository batches;
  @Autowired MenuRepository menus;
  @Autowired StoreSettingsRepository settings;
  @Autowired PosUploadService pos;
  @Autowired PosReportService reports;
  @Autowired PosUploadRepository uploads;
  @Autowired PurchaseReceiptService receipts;
  @Autowired ClosingService closing;
  @Autowired ClosingNotificationRepository notifications;
  @Autowired NotificationDelivery delivery;
  @Autowired OpeningScheduler opening;
  @MockitoBean FcmService fcm;
  private User owner;
  private Ingredient cabbage;
  private Menu menu;
  private CustomUserDetails principal;
  private LocalDate today;

  @BeforeEach
  void setup() {
    clock.now = Instant.parse("2026-10-07T06:00:00Z");
    today = LocalDate.now(clock);
    when(fcm.deliver(nullable(String.class), anyString(), anyString(), anyMap()))
        .thenReturn("SENT");
    owner = users.save(new User("workflow_" + UUID.randomUUID(), "test", "테스트점주", Role.OWNER));
    principal =
        new CustomUserDetails(
            owner.getId(),
            owner.getUsername(),
            "test",
            List.of(new SimpleGrantedAuthority("ROLE_OWNER")));
    cabbage = ingredients.save(new Ingredient(owner, "배추", "g", new BigDecimal("3000")));
    batches.save(
        new InventoryBatch(
            cabbage,
            new BigDecimal("6000"),
            BigDecimal.ONE,
            today.minusDays(30),
            today.plusDays(10)));
    menu = new Menu(owner, "배추국", 8000);
    menu.addBom(new RecipeBom(menu, cabbage, new BigDecimal("0.5"), "kg"));
    menu = menus.save(menu);
    pos.map(owner.getId(), "DEMO001", menu.getId());
    settings.save(
        new StoreSettings(
            owner, LocalTime.of(11, 0), LocalTime.of(22, 0), DayOfWeekType.SAT, DayOfWeekType.SUN));
  }

  @AfterEach
  void cleanOwnFixtures() {
    clock.now = Instant.parse("2026-10-07T06:00:00Z");
    tx.execute(
        status -> {
          Long id = owner.getId();
          for (String entity : List.of("ClosingNotification", "PosUpload", "PosMenuMapping"))
            em.createQuery("delete from " + entity + " e where e.userId = :id")
                .setParameter("id", id)
                .executeUpdate();
          em.createQuery("delete from PurchaseOrder p where p.user.id = :id")
              .setParameter("id", id)
              .executeUpdate();
          em.createQuery("delete from StoreSettings s where s.user.id = :id")
              .setParameter("id", id)
              .executeUpdate();
          em.createQuery("delete from InventoryBatch b where b.ingredient.user.id = :id")
              .setParameter("id", id)
              .executeUpdate();
          menus.deleteAll(menus.findAllByUserIdWithBom(id));
          em.flush();
          em.createQuery("delete from Ingredient i where i.user.id = :id")
              .setParameter("id", id)
              .executeUpdate();
          users.deleteById(id);
          return null;
        });
  }

  private MockMultipartFile workbook(
      LocalDate day, String code, String name, int quantity, int amount) throws Exception {
    try (var wb = new XSSFWorkbook();
        var out = new ByteArrayOutputStream()) {
      var s = wb.createSheet("판매내역");
      var header = s.createRow(0);
      String[] labels = {"영업일", "메뉴코드", "메뉴명", "판매수량", "판매금액"};
      for (int i = 0; i < labels.length; i++) header.createCell(i).setCellValue(labels[i]);
      var r = s.createRow(1);
      r.createCell(0).setCellValue(day.toString());
      r.createCell(1).setCellValue(code);
      r.createCell(2).setCellValue(name);
      r.createCell(3).setCellValue(quantity);
      r.createCell(4).setCellValue(amount);
      wb.write(out);
      return new MockMultipartFile(
          "file",
          "pos.xlsx",
          "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
          out.toByteArray());
    }
  }

  private BigDecimal stock() {
    return batches.sumQuantityByIngredientId(cabbage.getId());
  }

  private Long purchase(String unit, String quantity, String price, String packageFields)
      throws Exception {
    String body =
        "{\"ingredientId\":"
            + cabbage.getId()
            + ",\"quantity\":"
            + quantity
            + ",\"baseUnit\":\""
            + unit
            + "\",\"unitPrice\":"
            + price
            + ",\"supplier\":\"네이버\",\"productName\":\"배추"
            + " 2kg\",\"sourceUrl\":\"https://example.com/product\""
            + packageFields
            + "}";
    var response =
        mvc.perform(
                post("/purchase-orders")
                    .with(user(principal))
                    .contentType("application/json")
                    .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.deliveryStatus").value("WAITING"))
            .andReturn();
    return json.readTree(response.getResponse().getContentAsString()).path("id").asLong();
  }

  private long alertCount() {
    return notifications.findAll().stream()
        .filter(n -> n.getUserId().equals(owner.getId()))
        .count();
  }

  @Test
  void uploadReflectsRecipeConsumptionAndReportSnapshots() throws Exception {
    var file = workbook(today, "DEMO001", "배추국", 8, 64000);
    mvc.perform(multipart("/pos/uploads").file(file).with(user(principal)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.salesQuantity").value(8))
        .andExpect(jsonPath("$.salesAmount").value(64000))
        .andExpect(jsonPath("$.ingredientConsumption[0].quantity").value(4000));
    assertThat(stock()).isEqualByComparingTo("2000");
    assertThat(alertCount()).isEqualTo(1);
    var n =
        notifications
            .findByUserIdAndEventKey(
                owner.getId(),
                "UPLOAD:"
                    + uploads
                        .findByUserIdAndBusinessDate(owner.getId(), today)
                        .orElseThrow()
                        .getId())
            .orElseThrow();
    assertThat(n.getDeliveryStatus()).isEqualTo("SENT");
    verify(fcm)
        .deliver(
            nullable(String.class),
            anyString(),
            anyString(),
            argThat(
                data ->
                    data.get("type").equals("UPLOAD")
                        && data.get("notificationId").equals(n.getId().toString())));
    // Editing tomorrow's recipe must not rewrite historical consumption or amounts.
    tx.execute(
        status -> {
          var m = menus.findAllByUserIdWithBom(owner.getId()).getFirst();
          m.getBom().getFirst().setRequiredQuantity(BigDecimal.ONE);
          m.setPrice(12000);
          return null;
        });
    mvc.perform(get("/reports/daily").param("businessDate", today.toString()).with(user(principal)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.uploaded").value(true))
        .andExpect(jsonPath("$.salesAmount").value(64000))
        .andExpect(jsonPath("$.ingredientConsumption[0].quantity").value(4000))
        .andExpect(jsonPath("$.currentInventory[0].quantity").value(2000));
    assertThat(reports.dailyAverage(owner.getId(), cabbage.getId()))
        .isEqualByComparingTo("133.333");
  }

  @Test
  void sameFileIsIdempotentAndSameDayReplacementIsRejected() throws Exception {
    var file = workbook(today, "DEMO001", "배추국", 8, 64000);
    var first = pos.upload(owner.getId(), file);
    var second = pos.upload(owner.getId(), file);
    assertThat(second.duplicate()).isTrue();
    assertThat(second.uploadId()).isEqualTo(first.uploadId());
    assertThat(stock()).isEqualByComparingTo("2000");
    assertThat(alertCount()).isEqualTo(1);
    mvc.perform(
            multipart("/pos/uploads")
                .file(workbook(today, "DEMO001", "배추국", 1, 8000))
                .with(user(principal)))
        .andExpect(status().isConflict());
    assertThat(stock()).isEqualByComparingTo("2000");
  }

  @Test
  void insufficientSecondIngredientRollsBackWholeUploadWithoutNotification() throws Exception {
    tx.execute(
        s -> {
          var onion = ingredients.save(new Ingredient(owner, "양파", "g", BigDecimal.TEN));
          var m = menus.findAllByUserIdWithBom(owner.getId()).getFirst();
          m.addBom(new RecipeBom(m, onion, new BigDecimal("100"), "g"));
          return null;
        });
    mvc.perform(
            multipart("/pos/uploads")
                .file(workbook(today, "DEMO001", "배추국", 8, 64000))
                .with(user(principal)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("양파")));
    assertThat(stock()).isEqualByComparingTo("6000");
    assertThat(uploads.findByUserIdAndBusinessDate(owner.getId(), today)).isEmpty();
    assertThat(alertCount()).isZero();
    verify(fcm, never()).deliver(any(), any(), any(), any());
  }

  @Test
  void unmappedMenuAndFutureDayDoNotChangeStock() throws Exception {
    mvc.perform(
            multipart("/pos/uploads")
                .file(workbook(today, "UNKNOWN", "배추국", 1, 8000))
                .with(user(principal)))
        .andExpect(status().isBadRequest());
    mvc.perform(
            multipart("/pos/uploads")
                .file(workbook(today.plusDays(1), "DEMO001", "배추국", 1, 8000))
                .with(user(principal)))
        .andExpect(status().isBadRequest());
    assertThat(stock()).isEqualByComparingTo("6000");
    assertThat(alertCount()).isZero();
  }

  @Test
  void recordingPurchaseDoesNotChangeStockAndFullReceiptAppliesOnce() throws Exception {
    Long id = purchase("kg", "2", "5000", "");
    assertThat(stock()).isEqualByComparingTo("6000");
    String body = "{\"receivedQuantity\":2000,\"expirationDate\":\"" + today.plusDays(10) + "\"}";
    mvc.perform(
            post("/purchase-orders/" + id + "/receive")
                .with(user(principal))
                .contentType("application/json")
                .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.deliveryStatus").value("RECEIVED"))
        .andExpect(jsonPath("$.receivedBatchId").isNumber());
    assertThat(stock()).isEqualByComparingTo("8000");
    mvc.perform(
            post("/purchase-orders/" + id + "/receive")
                .with(user(principal))
                .contentType("application/json")
                .content(body))
        .andExpect(status().isConflict());
    assertThat(stock()).isEqualByComparingTo("8000");
    mvc.perform(get("/purchase-orders").param("deliveryStatus", "WAITING").with(user(principal)))
        .andExpect(jsonPath("$.totalElements").value(0));
    mvc.perform(get("/purchase-orders").param("deliveryStatus", "RECEIVED").with(user(principal)))
        .andExpect(jsonPath("$.totalElements").value(1));
  }

  @Test
  void packageConversionAndPartialReceiptValidation() throws Exception {
    Long id = purchase("포장", "2", "10000", ",\"packageSize\":2,\"packageUnit\":\"kg\"");
    mvc.perform(
            post("/purchase-orders/" + id + "/receive")
                .with(user(principal))
                .contentType("application/json")
                .content(
                    "{\"receivedQuantity\":1000,\"expirationDate\":\""
                        + today.plusDays(10)
                        + "\"}"))
        .andExpect(status().isBadRequest());
    assertThat(stock()).isEqualByComparingTo("6000");
    receipts.receive(
        owner.getId(),
        id,
        new PurchaseReceiptService.Receipt(new BigDecimal("4000"), today.plusDays(10), null));
    assertThat(stock()).isEqualByComparingTo("10000");
  }

  @Test
  void purchaseRejectsIncompatibleUnitsAndNegativeInput() throws Exception {
    for (String body :
        List.of(
            "{\"ingredientId\":"
                + cabbage.getId()
                + ",\"quantity\":1,\"baseUnit\":\"ml\",\"unitPrice\":100,\"supplier\":\"네이버\"}",
            "{\"ingredientId\":"
                + cabbage.getId()
                + ",\"quantity\":-1,\"baseUnit\":\"g\",\"unitPrice\":100,\"supplier\":\"네이버\"}")) {
      mvc.perform(
              post("/purchase-orders")
                  .with(user(principal))
                  .contentType("application/json")
                  .content(body))
          .andExpect(status().isBadRequest());
    }
    assertThat(stock()).isEqualByComparingTo("6000");
  }

  @Test
  void concurrentUploadsDeduplicateAndConcurrentReceiptsIncreaseOnce() throws Exception {
    var file = workbook(today, "DEMO001", "배추국", 8, 64000);
    try (var pool = Executors.newFixedThreadPool(6)) {
      var tasks = new ArrayList<Callable<PosContracts.UploadResult>>();
      for (int i = 0; i < 6; i++) tasks.add(() -> pos.upload(owner.getId(), file));
      var results = pool.invokeAll(tasks);
      int fresh = 0;
      for (var f : results) if (!f.get(20, TimeUnit.SECONDS).duplicate()) fresh++;
      assertThat(fresh).isEqualTo(1);
    }
    assertThat(stock()).isEqualByComparingTo("2000");
    assertThat(alertCount()).isEqualTo(1);
    Long id = purchase("kg", "2", "5000", "");
    try (var pool = Executors.newFixedThreadPool(6)) {
      var tasks = new ArrayList<Callable<Boolean>>();
      for (int i = 0; i < 6; i++)
        tasks.add(
            () -> {
              try {
                receipts.receive(
                    owner.getId(),
                    id,
                    new PurchaseReceiptService.Receipt(
                        new BigDecimal("2000"), today.plusDays(10), null));
                return true;
              } catch (ResponseStatusException e) {
                assertThat(e.getStatusCode().value()).isEqualTo(409);
                return false;
              }
            });
      int successes = 0;
      for (var f : pool.invokeAll(tasks)) if (f.get(20, TimeUnit.SECONDS)) successes++;
      assertThat(successes).isEqualTo(1);
    }
    assertThat(stock()).isEqualByComparingTo("4000");
  }

  @Test
  void uploadAndOpeningHaveSeparateNotificationsAndOpeningDeduplicates() throws Exception {
    pos.upload(owner.getId(), workbook(today, "DEMO001", "배추국", 8, 64000));
    var first = closing.prepareOpening(owner.getId(), today.plusDays(1));
    var duplicate = closing.prepareOpening(owner.getId(), today.plusDays(1));
    assertThat(first.notificationId()).isEqualTo(duplicate.notificationId());
    assertThat(alertCount()).isEqualTo(2);
    assertThat(notifications.findById(first.notificationId()).orElseThrow().getDeliveryStatus())
        .isEqualTo("SENT");
    verify(fcm, times(2)).deliver(nullable(String.class), anyString(), anyString(), anyMap());
  }

  @Test
  void openingSchedulerSupportsPreviousDayForEarlyOpening() {
    tx.execute(
        s -> {
          var conf = settings.findByUserId(owner.getId()).orElseThrow();
          conf.setOpenTime(LocalTime.of(2, 0));
          return null;
        });
    clock.now = today.atTime(23, 0).atZone(clock.getZone()).toInstant();
    opening.check();
    opening.check();
    assertThat(notifications.findByUserIdAndEventKey(owner.getId(), "OPENING:" + today.plusDays(1)))
        .isPresent();
    assertThat(alertCount()).isEqualTo(1);
  }

  @Test
  void fcmUnavailableIsNotReportedAsSentAndCanRetryAfterTokenRegistration() throws Exception {
    when(fcm.deliver(nullable(String.class), anyString(), anyString(), anyMap()))
        .thenReturn("WAITING_FOR_TOKEN");
    var result = pos.upload(owner.getId(), workbook(today, "DEMO001", "배추국", 8, 64000));
    var n =
        notifications
            .findByUserIdAndEventKey(owner.getId(), "UPLOAD:" + result.uploadId())
            .orElseThrow();
    assertThat(n.getDeliveryStatus()).isEqualTo("WAITING_FOR_TOKEN");
    assertThat(n.getSentAt()).isNull();
    clock.now = clock.now.plusSeconds(61);
    when(fcm.deliver(nullable(String.class), anyString(), anyString(), anyMap()))
        .thenReturn("SENT");
    delivery.send(n.getId());
    assertThat(notifications.findById(n.getId()).orElseThrow().getDeliveryStatus())
        .isEqualTo("SENT");
  }

  @Test
  void failedDeliveryRetriesAtMostThreeTimesAndOldUnsentAlertsExpire() throws Exception {
    when(fcm.deliver(nullable(String.class), anyString(), anyString(), anyMap()))
        .thenReturn("FAILED");
    var r = pos.upload(owner.getId(), workbook(today, "DEMO001", "배추국", 8, 64000));
    var n =
        notifications
            .findByUserIdAndEventKey(owner.getId(), "UPLOAD:" + r.uploadId())
            .orElseThrow();
    for (int i = 0; i < 3; i++) {
      clock.now = clock.now.plusSeconds(301);
      delivery.send(n.getId());
    }
    assertThat(notifications.findById(n.getId()).orElseThrow().getAttempts()).isEqualTo(3);
    verify(fcm, times(3)).deliver(nullable(String.class), anyString(), anyString(), anyMap());
    var next = closing.prepareOpening(owner.getId(), today.plusDays(1));
    clock.now = clock.now.plusSeconds(86401);
    delivery.send(next.notificationId());
    assertThat(notifications.findById(next.notificationId()).orElseThrow().getDeliveryStatus())
        .isEqualTo("EXPIRED");
  }

  @Test
  void expiredAndFutureInboundBatchesCannotHideShortage() throws Exception {
    pos.upload(owner.getId(), workbook(today, "DEMO001", "배추국", 8, 64000));
    batches.save(
        new InventoryBatch(
            cabbage,
            new BigDecimal("9000"),
            BigDecimal.ONE,
            today.minusDays(20),
            today.minusDays(1)));
    batches.save(
        new InventoryBatch(
            cabbage,
            new BigDecimal("9000"),
            BigDecimal.ONE,
            today.plusDays(1),
            today.plusDays(10)));
    var item =
        closing.recommendations(owner.getId(), today).items().stream()
            .filter(x -> x.ingredientId().equals(cabbage.getId()))
            .findFirst()
            .orElseThrow();
    assertThat(item.currentStock()).isEqualByComparingTo("2000");
    assertThat(stock()).isEqualByComparingTo("20000");
    mvc.perform(
            multipart("/pos/uploads")
                .file(workbook(today.minusDays(1), "DEMO001", "배추국", 30, 240000))
                .with(user(principal)))
        .andExpect(status().isConflict());
    assertThat(stock()).isEqualByComparingTo("20000");
  }

  @Test
  void authenticationOwnershipAndUnuploadedReportAreExplicit() throws Exception {
    mvc.perform(get("/reports/daily").param("businessDate", today.toString()))
        .andExpect(status().isUnauthorized());
    mvc.perform(get("/reports/daily").param("businessDate", today.toString()).with(user(principal)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.uploaded").value(false))
        .andExpect(jsonPath("$.salesQuantity").value(0));
    var other = users.save(new User("other_" + UUID.randomUUID(), "test", "다른점주", Role.OWNER));
    var otherPrincipal =
        new CustomUserDetails(
            other.getId(),
            other.getUsername(),
            "test",
            List.of(new SimpleGrantedAuthority("ROLE_OWNER")));
    Long id = purchase("kg", "2", "5000", "");
    mvc.perform(
            post("/purchase-orders/" + id + "/receive")
                .with(user(otherPrincipal))
                .contentType("application/json")
                .content(
                    "{\"receivedQuantity\":2000,\"expirationDate\":\""
                        + today.plusDays(10)
                        + "\"}"))
        .andExpect(status().isNotFound());
    var event = closing.prepareOpening(owner.getId(), today.plusDays(1));
    mvc.perform(get("/closing/notifications/" + event.notificationId()).with(user(otherPrincipal)))
        .andExpect(status().isNotFound());
    assertThat(stock()).isEqualByComparingTo("6000");
  }
}
