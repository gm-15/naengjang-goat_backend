package com.naengjang_goat.inventory_system.order.service;

import com.naengjang_goat.inventory_system.global.lock.LockStrategyFactory;
import com.naengjang_goat.inventory_system.global.util.UnitConverter;
import com.naengjang_goat.inventory_system.menu.domain.Menu;
import com.naengjang_goat.inventory_system.menu.domain.RecipeBom;
import com.naengjang_goat.inventory_system.menu.repository.MenuRepository;
import com.naengjang_goat.inventory_system.order.domain.Order;
import com.naengjang_goat.inventory_system.order.domain.OrderItem;
import com.naengjang_goat.inventory_system.order.dto.DeductedBatchInfo;
import com.naengjang_goat.inventory_system.order.dto.OrderItemRequest;
import com.naengjang_goat.inventory_system.order.dto.OrderRequest;
import com.naengjang_goat.inventory_system.order.dto.OrderResponse;
import com.naengjang_goat.inventory_system.order.repository.OrderRepository;
import com.naengjang_goat.inventory_system.user.domain.User;
import com.naengjang_goat.inventory_system.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository         orderRepository;
    private final MenuRepository          menuRepository;
    private final UserRepository          userRepository;
    private final LockStrategyFactory     lockStrategyFactory;
    private final StockDeductionService   stockDeductionService;
    private final UnitConverter           unitConverter;
    private final com.naengjang_goat.inventory_system.workflow.InventoryGate gate;

    /**
     * 주문 처리 — 핵심 흐름:
     * 1. 요청된 메뉴별 BOM을 순회
     * 2. 각 재료에 대해 LockStrategy로 FIFO 재고 차감 (StockDeductionService)
     * 3. 차감된 배치 정보를 수집해 응답에 포함
     * 4. Order / OrderItem 저장
     *
     * 전체 주문과 재료 차감을 하나의 트랜잭션으로 처리한다. 점주 행 잠금으로
     * POS 업로드·입고와 직렬화하며, 재료 중 하나라도 실패하면 전체를 롤백한다.
     *
     * @param userId  MockAuthFilter가 주입한 점주 ID
     * @param request 채널 타입 + 주문 항목 목록
     */
    @Transactional(rollbackFor = Exception.class)
    public OrderResponse processOrder(Long userId, OrderRequest request) throws Exception {
        gate.lock(userId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자: " + userId));

        int totalAmount = 0;
        Order order = new Order(user, request.channelType(), 0);
        List<DeductedBatchInfo> allDeducted = new ArrayList<>();

        for (OrderItemRequest itemReq : request.items()) {
            Menu menu = menuRepository.findByIdWithBom(itemReq.menuId())
                    .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 메뉴: " + itemReq.menuId()));

            if (!menu.getUser().getId().equals(userId) || itemReq.quantity()<=0) throw new IllegalArgumentException("메뉴 소유권/수량을 확인하세요");
            // BOM의 각 재료를 주문 수량만큼 FIFO 차감
            for (RecipeBom bom : menu.getBom()) {
                BigDecimal totalNeeded = bom.getRequiredQuantity()
                        .multiply(BigDecimal.valueOf(itemReq.quantity()));

                // BOM 단위 → 재료 base 단위로 변환
                BigDecimal neededInBase = unitConverter.convert(bom.getUnit(), totalNeeded, bom.getIngredient().getBaseUnit());

                String lockKey = "ingredient:" + bom.getIngredient().getId();
                List<DeductedBatchInfo> deducted = lockStrategyFactory.getCurrent()
                        .executeWithLock(lockKey, () ->
                                stockDeductionService.deductFifo(bom.getIngredient().getId(), neededInBase)
                        );
                allDeducted.addAll(deducted);
            }

            OrderItem item = new OrderItem(order, menu, itemReq.quantity());
            order.addItem(item);
            totalAmount += menu.getPrice() * itemReq.quantity();
        }

        order.setTotalAmount(totalAmount);
        Order saved = orderRepository.save(order);
        return OrderResponse.from(saved, allDeducted);
    }

    /**
     * 점주별 주문 이력 조회.
     */
    @Transactional(readOnly = true)
    public List<OrderResponse> getOrders(Long userId) {
        return orderRepository.findAll().stream()
                .filter(o -> o.getUser().getId().equals(userId))
                .map(OrderResponse::from)
                .toList();
    }
}
