package com.naengjang_goat.inventory_system.global.lock;

import java.util.concurrent.Callable;

public interface LockStrategy {

    /**
     * 락을 획득한 뒤 task를 실행하고 결과를 반환한다.
     *
     * @param key  락 식별자 (재료 ID 등)
     * @param task 실행할 비즈니스 로직
     * @param <T>  반환 타입
     */
    <T> T executeWithLock(String key, Callable<T> task) throws Exception;

    LockType getType();
}
