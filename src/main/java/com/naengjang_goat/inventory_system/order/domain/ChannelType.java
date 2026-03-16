package com.naengjang_goat.inventory_system.order.domain;

/**
 * 주문 채널 타입 (v2.1)
 * 다채널 동시성 시나리오의 근거 enum
 * POS / DELIVERY / KIOSK 가 동시에 같은 재고를 차감하려는 상황을 재현
 */
public enum ChannelType {
    POS,       // 카운터 직접 주문
    DELIVERY,  // 배달앱 주문 (배민, 쿠팡이츠 등)
    KIOSK      // 테이블 오더
}
