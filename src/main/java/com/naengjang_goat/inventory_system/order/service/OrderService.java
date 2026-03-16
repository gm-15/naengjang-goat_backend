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

    /**
     * 주문 처리 — 핵심 흐름:
     * 1. 요청된 메뉴별 BOM을 순회
     * 2. 각 재료에 대해 LockStrategy로 FIFO 재고 차감 (StockDeductionService)
     * 3. 차감된 배치 정보를 수집해 응답에 포함
     * 4. Order / OrderItem 저장
     *
     * ※ @Transactional 없음: 락 범위 안에서 StockDeductionService가 독립 트랜잭션을 열고
     *   즉시 커밋함. 다음 스레드가 락을 획득하면 항상 최신 커밋 값을 읽는다.
     *   (외부 트랜잭션이 있으면 REQUIRES_NEW로 인한 커넥션 2개 점유 → 풀 데드락 위험)
     *
     * @param userId  MockAuthFilter가 주입한 점주 ID
     * @param request 채널 타입 + 주문 항목 목록
     */
    public OrderResponse processOrder(Long userId, OrderRequest request) throws Exception {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자: " + userId));

        int totalAmount = 0;
        Order order = new Order(user, request.channelType(), 0);
        List<DeductedBatchInfo> allDeducted = new ArrayList<>();

        for (OrderItemRequest itemReq : request.items()) {
            Menu menu = menuRepository.findByIdWithBom(itemReq.menuId())
                    .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 메뉴: " + itemReq.menuId()));

            // BOM의 각 재료를 주문 수량만큼 FIFO 차감
            for (RecipeBom bom : menu.getBom()) {
                BigDecimal totalNeeded = bom.getRequiredQuantity()
                        .multiply(BigDecimal.valueOf(itemReq.quantity()));

                // BOM 단위 → 재료 base 단위로 변환
                BigDecimal neededInBase = unitConverter.toBase(bom.getUnit(), totalNeeded);

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
