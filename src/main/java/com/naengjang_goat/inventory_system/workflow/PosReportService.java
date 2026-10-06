package com.naengjang_goat.inventory_system.workflow;

import static com.naengjang_goat.inventory_system.workflow.PosContracts.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.naengjang_goat.inventory_system.inventory.repository.*;
import java.math.*;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PosReportService {
  private final PosUploadRepository uploads;
  private final WorkflowJson json;
  private final IngredientRepository ingredients;
  private final InventoryBatchRepository batches;
  private final Clock clock;

  @Transactional(readOnly = true)
  public DailyReport daily(Long userId, LocalDate date) {
    var upload = uploads.findByUserIdAndBusinessDate(userId, date);
    List<Sale> sales =
        upload
            .map(u -> json.<List<Sale>>read(u.getSalesJson(), new TypeReference<>() {}))
            .orElse(List.of());
    List<Consumption> usage =
        upload
            .map(
                u -> json.<List<Consumption>>read(u.getConsumptionJson(), new TypeReference<>() {}))
            .orElse(List.of());
    List<Stock> stock =
        ingredients.findAllByUserIdWithFetch(userId).stream()
            .map(
                i ->
                    new Stock(
                        i.getId(),
                        i.getName(),
                        i.getBaseUnit(),
                        batches.sumQuantityByIngredientId(i.getId())))
            .toList();
    return new DailyReport(
        date,
        upload.isPresent(),
        sales.stream().mapToInt(Sale::quantity).sum(),
        sales.stream().map(Sale::salesAmount).reduce(BigDecimal.ZERO, BigDecimal::add),
        sales,
        usage,
        stock,
        LocalDateTime.now(clock),
        upload.map(PosUpload::getCreatedAt).orElse(null),
        uploads
            .findFirstByUserIdOrderByBusinessDateDescCreatedAtDesc(userId)
            .map(PosUpload::getBusinessDate)
            .orElse(null));
  }

  @Transactional(readOnly = true)
  public BigDecimal dailyAverage(Long userId, Long ingredientId) {
    LocalDate today = LocalDate.now(clock);
    BigDecimal total = BigDecimal.ZERO;
    for (var upload :
        uploads.findByUserIdAndBusinessDateBetweenOrderByBusinessDateAsc(
            userId, today.minusDays(29), today)) {
      List<Consumption> items = json.read(upload.getConsumptionJson(), new TypeReference<>() {});
      for (var item : items)
        if (item.ingredientId().equals(ingredientId)) total = total.add(item.quantity());
    }
    return total.divide(BigDecimal.valueOf(30), 3, RoundingMode.HALF_UP);
  }
}
