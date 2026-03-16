package com.naengjang_goat.inventory_system.global.lock;

import com.naengjang_goat.inventory_system.global.lock.impl.NoLockStrategy;
import com.naengjang_goat.inventory_system.global.lock.impl.PessimisticLockStrategy;
import com.naengjang_goat.inventory_system.global.lock.impl.RedissonLockStrategy;
import com.naengjang_goat.inventory_system.global.lock.impl.SpinLockStrategy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LockStrategyFactory {

    private final NoLockStrategy noLockStrategy;
    private final SpinLockStrategy spinLockStrategy;
    private final RedissonLockStrategy redissonLockStrategy;
    private final PessimisticLockStrategy pessimisticLockStrategy;
    private final LockStrategyHolder holder;

    public LockStrategy getCurrent() {
        return get(holder.getCurrentType());
    }

    public LockStrategy get(LockType type) {
        return switch (type) {
            case NONE        -> noLockStrategy;
            case SPIN        -> spinLockStrategy;
            case REDISSON    -> redissonLockStrategy;
            case PESSIMISTIC -> pessimisticLockStrategy;
        };
    }
}
