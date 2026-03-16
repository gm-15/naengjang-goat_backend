package com.naengjang_goat.inventory_system.global.lock;

public enum LockType {
    NONE,           // 락 없음 — 동시성 붕괴 재현용
    SPIN,           // Redis SETNX 스핀락
    REDISSON,       // Redisson + Circuit Breaker (운영 기본)
    PESSIMISTIC     // DB 비관적 락 (Circuit Breaker OPEN 시 fallback)
}
