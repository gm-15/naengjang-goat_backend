package com.naengjang_goat.inventory_system.workflow;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public final class PosContracts {
  private PosContracts() {}

  public record Sale(
      Long menuId, String menuName, String posCode, int quantity, BigDecimal salesAmount) {}

  public record Consumption(
      Long ingredientId, String ingredientName, String baseUnit, BigDecimal quantity) {}

  public record UploadResult(
      Long uploadId,
      LocalDate businessDate,
      boolean duplicate,
      int salesQuantity,
      BigDecimal salesAmount,
      List<Sale> menuSales,
      List<Consumption> ingredientConsumption,
      LocalDateTime reflectedAt) {}

  public record Stock(
      Long ingredientId, String ingredientName, String baseUnit, BigDecimal quantity) {}

  public record DailyReport(
      LocalDate businessDate,
      boolean uploaded,
      int salesQuantity,
      BigDecimal salesAmount,
      List<Sale> menuSales,
      List<Consumption> ingredientConsumption,
      List<Stock> currentInventory,
      LocalDateTime inventoryAsOf,
      LocalDateTime reflectedAt,
      LocalDate lastUploadedBusinessDate) {}

  public record MappingRequest(@jakarta.validation.constraints.NotNull Long menuId) {}
}
