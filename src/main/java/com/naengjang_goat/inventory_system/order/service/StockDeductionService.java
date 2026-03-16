package com.naengjang_goat.inventory_system.order.service;

import com.naengjang_goat.inventory_system.global.lock.LockStrategyHolder;
import com.naengjang_goat.inventory_system.global.lock.LockType;
import com.naengjang_goat.inventory_system.global.lock.impl.PessimisticLockStrategy;
import com.naengjang_goat.inventory_system.inventory.domain.InventoryBatch;
import com.naengjang_goat.inventory_system.inventory.repository.InventoryBatchRepository;
import com.naengjang_goat.inventory_system.order.dto.DeductedBatchInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 재고 차감 전용 빈.
 *
 * processOrder는 @Transactional 없이 실행됨.
 * 따라서 이 메서드는 락 범위 안에서 독립 트랜잭션으로 실행되고 즉시 커밋됨.
 * → 다음 스레드가 락을 획득하면 항상 최신 커밋 값을 읽는다.
 *
 * PESSIMISTIC: PessimisticLockStrategy가 @Transactional 컨텍스트를 제공하면
 *   REQUIRED 전파로 합류 → SELECT FOR UPDATE가 해당 트랜잭션 내에서 실행됨.
 *
 * 반환값: 차감에 사용된 배치 목록 (POS 화면 표시용)
 */
@Service
@RequiredArgsConstructor
public class StockDeductionService {

    private final InventoryBatchRepository batchRepository;
    private final LockStrategyHolder       lockStrategyHolder;

    @Transactional
    public List<DeductedBatchInfo> deductFifo(Long ingredientId, BigDecimal needed) {
        // LockStrategyHolder 직접 지정 or Circuit Breaker fallback 경로 모두 커버
        boolean isPessimistic = lockStrategyHolder.getCurrentType() == LockType.PESSIMISTIC
                || Boolean.TRUE.equals(PessimisticLockStrategy.ACTIVE.get());
        List<InventoryBatch> batches = isPessimistic
                ? batchRepository.findAllByIngredientIdWithPessimisticLock(ingredientId)
                : batchRepository.findAllByIngredientIdAndQuantityGreaterThanOrderByExpirationDateAsc(
                        ingredientId, BigDecimal.ZERO);

        List<DeductedBatchInfo> deducted = new ArrayList<>();
        BigDecimal remaining = needed;

        for (InventoryBatch batch : batches) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;

            BigDecimal used;
            if (batch.getQuantity().compareTo(remaining) >= 0) {
                used = remaining;
                batch.setQuantity(batch.getQuantity().subtract(remaining));
                remaining = BigDecimal.ZERO;
            } else {
                used = batch.getQuantity();
                remaining = remaining.subtract(batch.getQuantity());
                batch.setQuantity(BigDecimal.ZERO);
            }
            deducted.add(new DeductedBatchInfo(batch.getId(), used));
        }

        if (remaining.compareTo(BigDecimal.ZERO) > 0) {
            throw new IllegalStateException(
                    "재고 부족 — ingredientId=" + ingredientId + ", 부족량=" + remaining);
        }

        return deducted;
    }
}
