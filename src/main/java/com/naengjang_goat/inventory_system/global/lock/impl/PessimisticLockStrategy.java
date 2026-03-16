package com.naengjang_goat.inventory_system.global.lock.impl;

import com.naengjang_goat.inventory_system.global.lock.LockStrategy;
import com.naengjang_goat.inventory_system.global.lock.LockType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.Callable;

/**
 * DB 비관적 락 (PESSIMISTIC_WRITE).
 *
 * 두 가지 경로로 호출됨:
 *   1. LockStrategyHolder.currentType = PESSIMISTIC 일 때 직접 호출
 *   2. RedissonLockStrategy.fallbackToDb() — Redis 장애 시 Circuit Breaker fallback
 *
 * 어느 경로로 호출되든 ACTIVE ThreadLocal을 true로 설정한다.
 * StockDeductionService.deductFifo()가 이를 보고 SELECT FOR UPDATE 쿼리를 선택한다.
 */
@Component
public class PessimisticLockStrategy implements LockStrategy {

    /**
     * 현재 스레드가 PESSIMISTIC 락 범위 안에 있음을 표시.
     * LockStrategyHolder.currentType 이 REDISSON이어도 (fallback 경로)
     * SELECT FOR UPDATE가 사용되도록 하는 신호.
     */
    public static final ThreadLocal<Boolean> ACTIVE = ThreadLocal.withInitial(() -> false);

    @Override
    @Transactional
    public <T> T executeWithLock(String key, Callable<T> task) throws Exception {
        ACTIVE.set(true);
        try {
            return task.call();
        } finally {
            ACTIVE.remove();
        }
    }

    @Override
    public LockType getType() {
        return LockType.PESSIMISTIC;
    }
}
