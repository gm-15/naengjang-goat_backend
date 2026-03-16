package com.naengjang_goat.inventory_system.global.lock.impl;

import com.naengjang_goat.inventory_system.global.lock.LockStrategy;
import com.naengjang_goat.inventory_system.global.lock.LockType;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

/**
 * Redisson 분산락 + Resilience4j Circuit Breaker.
 *
 * Circuit Breaker 인스턴스명: redisLock
 *   - slidingWindowSize=10, failureRateThreshold=50%
 *   - waitDurationInOpenState=10s
 *
 * OPEN 상태 시 fallback → PessimisticLockStrategy로 자동 전환.
 */
@Component
@RequiredArgsConstructor
public class RedissonLockStrategy implements LockStrategy {

    private static final String PREFIX       = "redisson:lock:";
    private static final long   WAIT_SEC     = 5L;
    private static final long   LEASE_SEC    = 10L;

    private final RedissonClient redissonClient;
    private final PessimisticLockStrategy pessimisticLockStrategy;

    @Override
    @CircuitBreaker(name = "redisLock", fallbackMethod = "fallbackToDb")
    public <T> T executeWithLock(String key, Callable<T> task) throws Exception {
        RLock lock = redissonClient.getLock(PREFIX + key);
        boolean acquired = lock.tryLock(WAIT_SEC, LEASE_SEC, TimeUnit.SECONDS);
        if (!acquired) {
            throw new IllegalStateException("Redisson 락 획득 실패: " + key);
        }
        try {
            return task.call();
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * Circuit Breaker OPEN 시 자동 호출되는 fallback.
     * PessimisticLockStrategy로 위임한다.
     */
    @SuppressWarnings("unused")
    public <T> T fallbackToDb(String key, Callable<T> task, Throwable t) throws Exception {
        return pessimisticLockStrategy.executeWithLock(key, task);
    }

    @Override
    public LockType getType() {
        return LockType.REDISSON;
    }
}
