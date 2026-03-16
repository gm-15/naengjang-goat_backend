package com.naengjang_goat.inventory_system.global.lock;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicReference;

/**
 * 런타임에 락 전략을 원자적으로 교체한다.
 * 테스트 시 setCurrentType()으로 즉시 전환 가능.
 */
@Component
public class LockStrategyHolder {

    private final AtomicReference<LockType> current = new AtomicReference<>(LockType.REDISSON);

    public LockType getCurrentType() {
        return current.get();
    }

    public void setCurrentType(LockType type) {
        current.set(type);
    }
}
