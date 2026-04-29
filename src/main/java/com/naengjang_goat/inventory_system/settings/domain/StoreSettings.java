package com.naengjang_goat.inventory_system.settings.domain;

import com.naengjang_goat.inventory_system.user.domain.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalTime;

/**
 * 점주 영업 설정 엔티티.
 *
 * 역할:
 *  - openTime / closeTime   → 알림 스케줄러가 "영업 X시간 전" 타이밍 계산
 *  - orderDay               → 소진 예정일 계산의 기준 (다음 발주 요일까지 남은 일수)
 *  - inventoryDay           → 재고 실사 기준일 (향후 활용)
 *
 * User 와 1:1 관계. user_id UNIQUE 제약 (V004 마이그레이션).
 */
@Entity
@Table(name = "store_settings")
@Getter
@Setter
@NoArgsConstructor
public class StoreSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "open_time", nullable = false)
    private LocalTime openTime;   // 영업 시작 (예: 11:00)

    @Column(name = "close_time", nullable = false)
    private LocalTime closeTime;  // 영업 종료 (예: 22:00)

    @Enumerated(EnumType.STRING)
    @Column(name = "order_day", length = 10, nullable = false)
    private DayOfWeekType orderDay;      // 발주 요일

    @Enumerated(EnumType.STRING)
    @Column(name = "inventory_day", length = 10, nullable = false)
    private DayOfWeekType inventoryDay;  // 재고 실사 요일

    public StoreSettings(User user, LocalTime openTime, LocalTime closeTime,
                         DayOfWeekType orderDay, DayOfWeekType inventoryDay) {
        this.user = user;
        this.openTime = openTime;
        this.closeTime = closeTime;
        this.orderDay = orderDay;
        this.inventoryDay = inventoryDay;
    }
}
