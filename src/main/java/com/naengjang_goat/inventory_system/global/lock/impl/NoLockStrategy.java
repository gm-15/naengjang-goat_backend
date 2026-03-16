package com.naengjang_goat.inventory_system.global.lock.impl;

import com.naengjang_goat.inventory_system.global.lock.LockStrategy;
import com.naengjang_goat.inventory_system.global.lock.LockType;
import org.springframework.stereotype.Component;

import java.util.concurrent.Callable;

/**
 * 락 없음 — 동시성 붕괴 재현 전용.
 * Phase 1 테스트: 500 스레드 동시 요청 시 재고 음수 발생 확인용.
 */
@Component
public class NoLockStrategy implements LockStrategy {

    @Override
    public <T> T executeWithLock(String key, Callable<T> task) throws Exception {
        return task.call();
    }

    @Override
    public LockType getType() {
        return LockType.NONE;
    }
}
