package com.naengjang_goat.inventory_system.workflow;

import com.naengjang_goat.inventory_system.inventory.domain.InventoryBatch;
import com.naengjang_goat.inventory_system.inventory.repository.InventoryBatchRepository;
import com.naengjang_goat.inventory_system.purchase.domain.PurchaseStatus;
import com.naengjang_goat.inventory_system.purchase.dto.PurchaseOrderResponse;
import com.naengjang_goat.inventory_system.purchase.repository.PurchaseOrderRepository;
import java.math.*;
import java.time.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class PurchaseReceiptService {
  private final InventoryGate gate;
  private final PurchaseOrderRepository purchases;
  private final InventoryBatchRepository batches;
  private final Clock clock;

  public record Receipt(
      @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.Positive
          BigDecimal receivedQuantity,
      @jakarta.validation.constraints.NotNull LocalDate expirationDate,
      LocalDate inboundDate) {}

  @Transactional
  public PurchaseOrderResponse receive(Long userId, Long id, Receipt request) {
    gate.lock(userId);
    var order =
        purchases
            .findById(id)
            .filter(p -> p.getUser().getId().equals(userId))
            .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "주문 없음"));
    if (!"WAITING".equals(order.getDeliveryStatus())
        || order.getStatus() == PurchaseStatus.CANCELLED)
      throw error(HttpStatus.CONFLICT, "입고 가능한 배송 대기 주문이 아닙니다");
    if (order.getExpectedQuantityBase() == null
        || order.getExpectedQuantityBase().compareTo(request.receivedQuantity()) != 0)
      throw error(HttpStatus.BAD_REQUEST, "이번 버전은 전체 수령만 지원합니다. 예상 기본단위 수량과 일치해야 합니다");
    LocalDate today = LocalDate.now(clock);
    LocalDate inbound = request.inboundDate() != null ? request.inboundDate() : today;
    if (request.expirationDate().isBefore(today)
        || inbound.isAfter(today)
        || inbound.isBefore(order.getOrderedAt())
        || inbound.isAfter(request.expirationDate()))
      throw error(HttpStatus.BAD_REQUEST, "입고일/유통기한을 확인하세요");
    var cost = order.getTotalAmount().divide(request.receivedQuantity(), 2, RoundingMode.HALF_UP);
    if (cost.compareTo(new BigDecimal("99999999.99")) > 0)
      throw error(HttpStatus.BAD_REQUEST, "기본단위당 입고 단가 범위를 확인하세요");
    var batch =
        new InventoryBatch(
            order.getIngredient(),
            request.receivedQuantity(),
            cost,
            inbound,
            request.expirationDate());
    batches.saveAndFlush(batch);
    order.setDeliveryStatus("RECEIVED");
    order.setReceivedAt(LocalDateTime.now(clock));
    order.setReceivedBatchId(batch.getId());
    return PurchaseOrderResponse.from(order);
  }

  private ResponseStatusException error(HttpStatus status, String message) {
    return new ResponseStatusException(status, message);
  }
}
