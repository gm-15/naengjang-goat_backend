package com.naengjang_goat.inventory_system.order.controller;

import com.naengjang_goat.inventory_system.global.security.CustomUserDetails;
import com.naengjang_goat.inventory_system.order.dto.OrderItemRequest;
import com.naengjang_goat.inventory_system.order.dto.OrderRequest;
import com.naengjang_goat.inventory_system.order.dto.OrderResponse;
import com.naengjang_goat.inventory_system.order.dto.PosOrderRequest;
import com.naengjang_goat.inventory_system.order.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 주문 처리 컨트롤러 (v2.2 — JWT 인증 복구)
 *
 * 인증: JWT Bearer Token
 */
@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /**
     * POST /orders
     * POS 프론트엔드용 단일 메뉴 주문.
     *
     * Request:  { "menuId": 1, "quantity": 2, "channelType": "POS" }
     * Response: { "orderId": 10, "status": "COMPLETED", "deductedBatches": [...] }
     */
    @PostMapping
    public ResponseEntity<OrderResponse> placeOrder(
            @AuthenticationPrincipal CustomUserDetails principal,
            @Valid @RequestBody PosOrderRequest request
    ) throws Exception {
        OrderRequest orderRequest = new OrderRequest(
                request.channelType(),
                List.of(new OrderItemRequest(request.menuId(), request.quantity()))
        );
        return ResponseEntity.ok(orderService.processOrder(principal.getId(), orderRequest));
    }

    /**
     * GET /orders
     * 점주 본인의 주문 이력 조회.
     */
    @GetMapping
    public ResponseEntity<List<OrderResponse>> getOrders(
            @AuthenticationPrincipal CustomUserDetails principal
    ) {
        return ResponseEntity.ok(orderService.getOrders(principal.getId()));
    }
}
