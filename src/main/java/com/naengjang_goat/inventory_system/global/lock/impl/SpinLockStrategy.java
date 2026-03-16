package com.naengjang_goat.inventory_system.global.lock.impl;

import com.naengjang_goat.inventory_system.global.lock.LockStrategy;
import com.naengjang_goat.inventory_system.global.lock.LockType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.Callable;

/**
 * Redis SETNX 스핀락.
 * 1ms 간격으로 폴링하며 최대 30초 대기.
 * (500 스레드 부하테스트 기준: 500 × ~10ms = ~5초 필요 → 여유 6배)
 */
@Component
@RequiredArgsConstructor
public class SpinLockStrategy implements LockStrategy {

    private static final String PREFIX       = "spin:lock:";
    private static final long   TIMEOUT_MS   = 30_000L;
    private static final long   SPIN_INTERVAL_MS = 1L;
    private static final Duration TTL        = Duration.ofSeconds(30);

    private final StringRedisTemplate redisTemplate;

    @Override
    public <T> T executeWithLock(String key, Callable<T> task) throws Exception {
        String lockKey = PREFIX + key;
        long deadline = System.currentTimeMillis() + TIMEOUT_MS;

        // 스핀 — 락 획득 시도
        while (!Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(lockKey, "1", TTL))) {
            if (System.currentTimeMillis() > deadline) {
                throw new IllegalStateException("SpinLock 획득 타임아웃: " + key);
            }
            //noinspection BusyWait
            Thread.sleep(SPIN_INTERVAL_MS);
        }

        try {
            return task.call();
        } finally {
            redisTemplate.delete(lockKey);
        }
    }

    @Override
    public LockType getType() {
        return LockType.SPIN;
    }
}
