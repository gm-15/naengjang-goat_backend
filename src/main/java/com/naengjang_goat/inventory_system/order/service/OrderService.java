package com.naengjang_goat.inventory_system.order.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.naengjang_goat.inventory_system.global.lock.LockStrategyFactory;
import com.naengjang_goat.inventory_system.global.util.UnitConverter;
import com.naengjang_goat.inventory_system.inventory.domain.InventoryBatch;
import com.naengjang_goat.inventory_system.inventory.repository.InventoryBatchRepository;
import com.naengjang_goat.inventory_system.menu.domain.Menu;
import com.naengjang_goat.inventory_system.menu.domain.RecipeBom;
import com.naengjang_goat.inventory_system.menu.repository.MenuRepository;
import com.naengjang_goat.inventory_system.order.domain.Order;
import com.naengjang_goat.inventory_system.order.domain.OrderItem;
import com.naengjang_goat.inventory_system.order.domain.OrderStatus;
import com.naengjang_goat.inventory_system.order.dto.DeductedBatchInfo;
import com.naengjang_goat.inventory_system.order.dto.OrderItemRequest;
import com.naengjang_goat.inventory_system.order.dto.OrderRequest;
import com.naengjang_goat.inventory_system.order.dto.OrderResponse;
import com.naengjang_goat.inventory_system.order.repository.OrderRepository;
import com.naengjang_goat.inventory_system.user.domain.User;
import com.naengjang_goat.inventory_system.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository           orderRepository;
    private final MenuRepository            menuRepository;
    private final UserRepository            userRepository;
    private final LockStrategyFactory       lockStrategyFactory;
    private final StockDeductionService     stockDeductionService;
    private final UnitConverter             unitConverter;
    private final InventoryBatchRepository  batchRepository;          // sim, 2026-06-04 — cancel 복구용
    private final ObjectMapper              objectMapper;             // sim, 2026-06-04 — deducted JSON

    /**
     * 주문 처리 — 핵심 흐름:
     * 1. 요청된 메뉴별 BOM을 순회
     * 2. 각 재료에 대해 LockStrategy로 FIFO 재고 차감 (StockDeductionService)
     * 3. 차감된 배치 정보를 수집해 응답에 포함
     * 4. Order / OrderItem 저장 (OrderItem 별 deductedBatches JSON 저장 — 취소 복구용)
     *
     * ※ @Transactional 없음: 락 범위 안에서 StockDeductionService가 독립 트랜잭션을 열고
     *   즉시 커밋함. 다음 스레드가 락을 획득하면 항상 최신 커밋 값을 읽는다.
     *
     * @param userId  점주 ID
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

            List<DeductedBatchInfo> itemDeducted = new ArrayList<>();

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
                itemDeducted.addAll(deducted);
                allDeducted.addAll(deducted);
            }

            OrderItem item = new OrderItem(order, menu, itemReq.quantity());
            // sim, 2026-06-04 — 취소 시 복구용 deducted 정보 JSON 저장
            item.setDeductedBatches(serializeDeducted(itemDeducted));
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

    /**
     * 주문 취소 + 재고 복구.
     *
     * 흐름:
     *  1. 주문 조회 + 소유 검증
     *  2. status 가 COMPLETED 가 아니면 거부 (이미 취소된 주문 등)
     *  3. OrderItem 마다 저장된 deductedBatches JSON 역직렬화
     *  4. 각 batch 의 quantity 를 deductedQuantity 만큼 복원 (배치 삭제 시 SKIP + WARN)
     *  5. order.status = CANCELED
     *
     * 동시성 주의: cancel 시점에 같은 batch 가 다른 주문으로 추가 차감 중일 가능성 — 향후 락 추가 검토.
     *
     * @author sim
     * @since 2026-06-04
     */
    @Transactional
    public void cancelOrder(Long userId, Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "주문 없음: " + orderId));

        if (!order.getUser().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "접근 권한 없음");
        }
        if (order.getOrderStatus() != OrderStatus.COMPLETED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "취소 불가 상태 — 현재 " + order.getOrderStatus());
        }

        int restoredBatches = 0;
        for (OrderItem item : order.getItems()) {
            List<DeductedBatchInfo> deducted = deserializeDeducted(item.getDeductedBatches());
            for (DeductedBatchInfo info : deducted) {
                var batchOpt = batchRepository.findById(info.batchId());
                if (batchOpt.isEmpty()) {
                    log.warn("[cancelOrder] 배치 삭제됨 — 복구 SKIP. orderId={} batchId={}",
                            orderId, info.batchId());
                    continue;
                }
                InventoryBatch batch = batchOpt.get();
                batch.setQuantity(batch.getQuantity().add(info.deductedQuantity()));
                batchRepository.save(batch);
                restoredBatches++;
            }
        }

        order.setOrderStatus(OrderStatus.CANCELED);
        orderRepository.save(order);
        log.info("[cancelOrder] orderId={} 취소 완료. 복구 batch={}", orderId, restoredBatches);
    }

    // ─── private helpers ────────────────────────────────────────────────────

    private String serializeDeducted(List<DeductedBatchInfo> list) {
        if (list == null || list.isEmpty()) return "[]";
        try {
            return objectMapper.writeValueAsString(list);
        } catch (JsonProcessingException e) {
            log.error("[OrderService] deducted 직렬화 실패", e);
            return "[]";
        }
    }

    private List<DeductedBatchInfo> deserializeDeducted(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<DeductedBatchInfo>>() {});
        } catch (JsonProcessingException e) {
            log.error("[OrderService] deducted 역직렬화 실패 — json={}", json, e);
            return List.of();
        }
    }
}
