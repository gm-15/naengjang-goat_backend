package com.naengjang_goat.inventory_system.order.controller;

import com.naengjang_goat.inventory_system.order.dto.OrderItemRequest;
import com.naengjang_goat.inventory_system.order.dto.OrderRequest;
import com.naengjang_goat.inventory_system.order.dto.OrderResponse;
import com.naengjang_goat.inventory_system.order.dto.PosOrderRequest;
import com.naengjang_goat.inventory_system.order.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 주문 처리 컨트롤러 (v2.1)
 * MockAuthFilter가 X-User-Id 헤더를 request attribute로 주입함.
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
     * Header:   X-User-Id: 1
     */
    @PostMapping
    public ResponseEntity<OrderResponse> placeOrder(
            @RequestAttribute("userId") Long userId,
            @Valid @RequestBody PosOrderRequest request
    ) throws Exception {
        // PosOrderRequest → OrderRequest(items 리스트) 어댑터
        OrderRequest orderRequest = new OrderRequest(
                request.channelType(),
                List.of(new OrderItemRequest(request.menuId(), request.quantity()))
        );
        return ResponseEntity.ok(orderService.processOrder(userId, orderRequest));
    }

    /**
     * GET /orders
     * 점주 본인의 주문 이력 조회.
     */
    @GetMapping
    public ResponseEntity<List<OrderResponse>> getOrders(
            @RequestAttribute("userId") Long userId
    ) {
        return ResponseEntity.ok(orderService.getOrders(userId));
    }
}
