package com.naengjang_goat.inventory_system.order.domain;

import com.naengjang_goat.inventory_system.user.domain.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 주문 이력 엔티티 (v2.1)
 * 구 SaleHistory 대체 — channel_type, order_status 추가
 *
 * channel_type: 다채널 동시성 시나리오의 핵심 필드
 *   POS      → 카운터 주문
 *   DELIVERY → 배달앱 주문 (배민, 쿠팡이츠 등)
 *   KIOSK    → 테이블 오더
 *
 * 주문 취소(CANCELED) 시 재고 복구 로직 필요
 */
@Entity
@Table(name = "orders") // 'order'는 SQL 예약어
@Getter
@Setter
@NoArgsConstructor
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel_type", nullable = false)
    private ChannelType channelType; // 주문 채널 — 다채널 동시성 핵심

    @Enumerated(EnumType.STRING)
    @Column(name = "order_status", nullable = false)
    private OrderStatus orderStatus;

    @Column(name = "total_amount", nullable = false)
    private Integer totalAmount;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    public Order(User user, ChannelType channelType, Integer totalAmount) {
        this.user = user;
        this.channelType = channelType;
        this.orderStatus = OrderStatus.COMPLETED;
        this.totalAmount = totalAmount;
        this.createdAt = LocalDateTime.now();
    }

    public void addItem(OrderItem item) {
        items.add(item);
        item.setOrder(this);
    }
}
