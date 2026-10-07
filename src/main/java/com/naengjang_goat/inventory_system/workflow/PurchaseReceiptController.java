package com.naengjang_goat.inventory_system.workflow;

import com.naengjang_goat.inventory_system.global.security.CustomUserDetails;
import com.naengjang_goat.inventory_system.purchase.dto.PurchaseOrderResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class PurchaseReceiptController {
  private final PurchaseReceiptService receipts;

  @PostMapping("/purchase-orders/{id}/receive")
  public PurchaseOrderResponse receive(
      @AuthenticationPrincipal CustomUserDetails user,
      @PathVariable Long id,
      @Valid @RequestBody PurchaseReceiptService.Receipt request) {
    return receipts.receive(user.getId(), id, request);
  }
}
