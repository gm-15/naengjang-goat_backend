package com.naengjang_goat.inventory_system.workflow;

import static com.naengjang_goat.inventory_system.workflow.PosContracts.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.naengjang_goat.inventory_system.global.util.UnitConverter;
import com.naengjang_goat.inventory_system.inventory.domain.*;
import com.naengjang_goat.inventory_system.inventory.repository.*;
import com.naengjang_goat.inventory_system.menu.domain.*;
import com.naengjang_goat.inventory_system.menu.repository.MenuRepository;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class PosUploadService {
  private final InventoryGate gate;
  private final PosExcelReader reader;
  private final PosUploadRepository uploads;
  private final PosMenuMappingRepository mappings;
  private final MenuRepository menus;
  private final InventoryBatchRepository batches;
  private final UnitConverter units;
  private final WorkflowJson json;
  private final Clock clock;
  private final ClosingService closing;

  @Transactional
  public UploadResult upload(Long userId, MultipartFile file) {
    gate.lock(userId);
    if (file.isEmpty() || file.getSize() > 5_000_000) throw bad("5MB 이하 파일만 지원합니다");
    String hash;
    try {
      hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(file.getBytes()));
    } catch (Exception e) {
      throw bad("파일을 읽을 수 없습니다");
    }
    var duplicate = uploads.findByUserIdAndFileHash(userId, hash);
    if (duplicate.isPresent()) return result(duplicate.get(), true);
    var rows = reader.read(file);
    LocalDate date = rows.getFirst().date();
    if (date.isAfter(LocalDate.now(clock))) throw bad("미래 영업일은 반영할 수 없습니다");
    if (uploads.findByUserIdAndBusinessDate(userId, date).isPresent())
      throw conflict("이미 반영한 영업일입니다. 하루 전체 자료를 한 번만 업로드하세요");
    Map<Long, Menu> owned = new HashMap<>();
    menus.findAllByUserIdWithBom(userId).forEach(m -> owned.put(m.getId(), m));
    List<Sale> sales = new ArrayList<>();
    Map<Long, Ingredient> ingredients = new TreeMap<>();
    Map<Long, BigDecimal> needed = new TreeMap<>();
    Set<Long> seenMenus = new HashSet<>();
    for (var row : rows) {
      Menu menu;
      if (!row.code().isBlank()) {
        var mapping =
            mappings
                .findByUserIdAndPosCode(userId, row.code())
                .orElseThrow(() -> bad(row.row() + "행: 연결되지 않은 메뉴코드 " + row.code()));
        menu = owned.get(mapping.getMenuId());
      } else {
        var matching = owned.values().stream().filter(m -> m.getName().equals(row.name())).toList();
        if (matching.size() != 1) throw bad(row.row() + "행: 고유한 메뉴명 또는 코드 연결이 필요합니다");
        menu = matching.getFirst();
      }
      if (menu == null || menu.getBom().isEmpty() || !menu.getName().equals(row.name()))
        throw bad(row.row() + "행: 메뉴명/레시피 연결을 확인하세요");
      if (!seenMenus.add(menu.getId())) throw bad(row.row() + "행: 같은 메뉴는 하루 합산 한 행으로 작성하세요");
      sales.add(new Sale(menu.getId(), menu.getName(), row.code(), row.quantity(), row.amount()));
      for (var bom : menu.getBom()) {
        Ingredient ingredient = bom.getIngredient();
        if (!ingredient.getUser().getId().equals(userId) || bom.getRequiredQuantity().signum() <= 0)
          throw bad("레시피 재료/소모량을 확인하세요");
        BigDecimal amount;
        try {
          amount =
              units.convert(
                  bom.getUnit(),
                  bom.getRequiredQuantity().multiply(BigDecimal.valueOf(row.quantity())),
                  ingredient.getBaseUnit());
        } catch (IllegalArgumentException e) {
          throw bad("레시피 단위 변환 불가: " + ingredient.getName());
        }
        ingredients.put(ingredient.getId(), ingredient);
        needed.merge(ingredient.getId(), amount, BigDecimal::add);
      }
    }
    // All stock is checked before writing; store gate serializes receipt/upload/other mutations.
    Map<Long, List<InventoryBatch>> available = new TreeMap<>();
    for (var entry : needed.entrySet()) {
      var stock = batches.findAllByIngredientIdWithPessimisticLock(entry.getKey());
      // An expired batch is not available for this upload's business date.
      stock =
          stock.stream()
              .filter(
                  b -> !b.getExpirationDate().isBefore(date) && !b.getInboundDate().isAfter(date))
              .toList();
      BigDecimal sum =
          stock.stream().map(InventoryBatch::getQuantity).reduce(BigDecimal.ZERO, BigDecimal::add);
      if (sum.compareTo(entry.getValue()) < 0)
        throw conflict(
            "재고 부족: "
                + ingredients.get(entry.getKey()).getName()
                + " / 필요 "
                + entry.getValue()
                + " / 보유 "
                + sum);
      available.put(entry.getKey(), stock);
    }
    List<Consumption> consumption = new ArrayList<>();
    for (var entry : needed.entrySet()) {
      BigDecimal remaining = entry.getValue();
      for (var batch : available.get(entry.getKey())) {
        BigDecimal used = batch.getQuantity().min(remaining);
        batch.setQuantity(batch.getQuantity().subtract(used));
        remaining = remaining.subtract(used);
        if (remaining.signum() == 0) break;
      }
      Ingredient i = ingredients.get(entry.getKey());
      consumption.add(new Consumption(i.getId(), i.getName(), i.getBaseUnit(), entry.getValue()));
    }
    PosUpload upload = new PosUpload();
    upload.setUserId(userId);
    upload.setBusinessDate(date);
    upload.setFileHash(hash);
    upload.setOriginalFilename(
        Objects.requireNonNull(file.getOriginalFilename())
            .substring(0, Math.min(255, file.getOriginalFilename().length())));
    upload.setSalesJson(json.write(sales));
    upload.setConsumptionJson(json.write(consumption));
    upload.setCreatedAt(LocalDateTime.now(clock));
    uploads.saveAndFlush(upload);
    closing.prepareUpload(userId, upload.getId(), date);
    return result(upload, false);
  }

  public UploadResult result(PosUpload upload, boolean duplicate) {
    List<Sale> sales = json.read(upload.getSalesJson(), new TypeReference<>() {});
    List<Consumption> consumption =
        json.read(upload.getConsumptionJson(), new TypeReference<>() {});
    return new UploadResult(
        upload.getId(),
        upload.getBusinessDate(),
        duplicate,
        sales.stream().mapToInt(Sale::quantity).sum(),
        sales.stream().map(Sale::salesAmount).reduce(BigDecimal.ZERO, BigDecimal::add),
        sales,
        consumption,
        upload.getCreatedAt());
  }

  @Transactional
  public PosMenuMapping map(Long userId, String code, Long menuId) {
    gate.lock(userId);
    if (code.isBlank() || code.length() > 100) throw bad("메뉴코드를 확인하세요");
    var menu = menus.findById(menuId).orElseThrow(() -> bad("메뉴 없음"));
    if (!menu.getUser().getId().equals(userId))
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "다른 점주의 메뉴입니다");
    var mapping = mappings.findByUserIdAndPosCode(userId, code).orElseGet(PosMenuMapping::new);
    mapping.setUserId(userId);
    mapping.setPosCode(code);
    mapping.setMenuId(menuId);
    return mappings.save(mapping);
  }

  private ResponseStatusException bad(String s) {
    return new ResponseStatusException(HttpStatus.BAD_REQUEST, s);
  }

  private ResponseStatusException conflict(String s) {
    return new ResponseStatusException(HttpStatus.CONFLICT, s);
  }
}
