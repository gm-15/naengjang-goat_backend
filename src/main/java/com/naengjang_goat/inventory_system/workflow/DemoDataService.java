package com.naengjang_goat.inventory_system.workflow;

import com.naengjang_goat.inventory_system.analysis.domain.MarketPrice;
import com.naengjang_goat.inventory_system.analysis.repository.MarketPriceRepository;
import com.naengjang_goat.inventory_system.inventory.domain.*;
import com.naengjang_goat.inventory_system.inventory.repository.*;
import com.naengjang_goat.inventory_system.menu.domain.*;
import com.naengjang_goat.inventory_system.menu.repository.MenuRepository;
import com.naengjang_goat.inventory_system.settings.domain.*;
import com.naengjang_goat.inventory_system.settings.repository.StoreSettingsRepository;
import com.naengjang_goat.inventory_system.user.repository.UserRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Synthetic, isolated demo fixtures. Does not overwrite or reset existing data. */
@Service
@Profile("demo")
@RequiredArgsConstructor
public class DemoDataService {
  private final InventoryGate gate;
  private final UserRepository users;
  private final IngredientRepository ingredients;
  private final InventoryBatchRepository batches;
  private final MenuRepository menus;
  private final PosMenuMappingRepository mappings;
  private final PosUploadRepository uploads;
  private final StoreSettingsRepository settings;
  private final MarketPriceRepository prices;
  private final WorkflowJson json;
  private final Clock clock;

  public record SetupResult(
      boolean alreadyInitialized,
      Long ingredientId,
      Long menuId,
      String posCode,
      LocalDate businessDate,
      BigDecimal initialStock,
      BigDecimal expectedConsumption,
      BigDecimal expectedStockAfterUpload) {}

  @Transactional
  public SetupResult setup(Long userId) {
    gate.lock(userId);
    var user = users.findById(userId).orElseThrow();
    if (!"demo".equals(user.getUsername()))
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "demo 계정 전용입니다");
    var previous = mappings.findByUserIdAndPosCode(userId, "DEMO001");
    if (previous.isPresent()) {
      var m =
          menus.findAllByUserIdWithBom(userId).stream()
              .filter(x -> x.getId().equals(previous.get().getMenuId()))
              .findFirst()
              .orElseThrow();
      return result(true, m.getBom().getFirst().getIngredient().getId(), m.getId());
    }
    if (uploads.findFirstByUserIdOrderByBusinessDateDescCreatedAtDesc(userId).isPresent()
        || !batches.findAllByUserIdWithFetch(userId).isEmpty()
        || settings.findByUserId(userId).isPresent())
      throw new ResponseStatusException(HttpStatus.CONFLICT, "기존 demo 데이터가 있습니다. 새 시연 DB에서 실행하세요");
    LocalDate today = LocalDate.now(clock);
    var ingredient = ingredients.save(new Ingredient(user, "시연용 배추", "g", new BigDecimal("3000")));
    ingredient.setKamisCategory("VEGETABLES");
    var menu = new Menu(user, "배추국", 8000);
    menu.addBom(new RecipeBom(menu, ingredient, new BigDecimal("500"), "g"));
    menus.saveAndFlush(menu);
    var mapping = new PosMenuMapping();
    mapping.setUserId(userId);
    mapping.setPosCode("DEMO001");
    mapping.setMenuId(menu.getId());
    mappings.save(mapping);
    // The batch is the opening balance after the synthetic historical consumption.
    batches.save(
        new InventoryBatch(
            ingredient,
            new BigDecimal("6000"),
            new BigDecimal("2"),
            today.minusDays(30),
            today.plusDays(10)));
    var orderDay = DayOfWeekType.values()[today.plusDays(3).getDayOfWeek().getValue() - 1];
    settings.save(
        new StoreSettings(
            user, LocalTime.of(11, 0), LocalTime.of(22, 0), orderDay, DayOfWeekType.SUN));
    for (int ago = 29; ago >= 1; ago--) {
      LocalDate date = today.minusDays(ago);
      var upload = new PosUpload();
      upload.setUserId(userId);
      upload.setBusinessDate(date);
      upload.setOriginalFilename("SYNTHETIC-demo-history");
      try {
        upload.setFileHash(
            HexFormat.of()
                .formatHex(
                    MessageDigest.getInstance("SHA-256")
                        .digest(("demo:" + userId + ":" + date).getBytes(StandardCharsets.UTF_8))));
      } catch (java.security.NoSuchAlgorithmException e) {
        throw new IllegalStateException("SHA-256 unavailable", e);
      }
      upload.setSalesJson(
          json.write(
              List.of(
                  new PosContracts.Sale(
                      menu.getId(), menu.getName(), "DEMO001", 2, new BigDecimal("16000")))));
      upload.setConsumptionJson(
          json.write(
              List.of(
                  new PosContracts.Consumption(
                      ingredient.getId(), ingredient.getName(), "g", new BigDecimal("1000")))));
      upload.setCreatedAt(date.atTime(22, 0));
      uploads.save(upload);
    }
    for (int ago = 29; ago >= 0; ago--) {
      var price =
          new MarketPrice(
              ingredient, null, ago == 0 ? "8000" : "10000", "1kg", today.minusDays(ago));
      price.setSource("DEMO");
      prices.save(price);
    }
    return result(false, ingredient.getId(), menu.getId());
  }

  private SetupResult result(boolean existing, Long ingredientId, Long menuId) {
    return new SetupResult(
        existing,
        ingredientId,
        menuId,
        "DEMO001",
        LocalDate.now(clock),
        new BigDecimal("6000"),
        new BigDecimal("4000"),
        new BigDecimal("2000"));
  }
}
