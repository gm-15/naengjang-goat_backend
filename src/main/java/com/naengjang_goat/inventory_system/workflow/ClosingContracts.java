package com.naengjang_goat.inventory_system.workflow;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public final class ClosingContracts {
  private ClosingContracts() {}

  public record Item(
      Long ingredientId,
      String ingredientName,
      String baseUnit,
      BigDecimal currentStock,
      BigDecimal dailyAvgSales,
      int nextOrderDayDistance,
      BigDecimal recommendedQuantity,
      LocalDate estimatedDepletionDate,
      boolean stockAlert,
      boolean buySignal,
      int priceDataCoverage,
      String priceReason,
      String reason) {}

  public record Recommendations(
      LocalDate businessDate,
      LocalDateTime generatedAt,
      LocalDate lastUploadedBusinessDate,
      LocalDateTime lastReflectedAt,
      boolean settingsConfigured,
      List<Item> items) {}

  public record AlertResult(
      Long notificationId, String type, LocalDate businessDate, String deliveryStatus) {}

  public record NotificationEvent(Long id) {}
}
