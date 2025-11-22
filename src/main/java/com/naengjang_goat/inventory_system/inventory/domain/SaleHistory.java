package com.naengjang_goat.inventory_system.inventory.domain;

import com.naengjang_goat.inventory_system.user.domain.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class SaleHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 판매 시점
    @Column(nullable = false)
    private LocalDateTime saleTimestamp = LocalDateTime.now();

    // 총 금액
    @Column(nullable = false)
    private int totalAmount;

    // 점주와 N:1
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    // 어떤 메뉴가 팔렸는지 (1:N sale_item 이 없으므로 recipe만 저장)
    @Column(nullable = false)
    private Long recipeId;

    // 🚨 핵심 수정: DB 컬럼명 'quantity'와 정확히 일치시킴
    // 기존: name = "quantity_sold" -> 수정: name = "quantity"
    @Column(name = "quantity", nullable = false)
    private int quantity;

    public SaleHistory(User user, Long recipeId, int quantity, int totalAmount) {
        this.user = user;
        this.recipeId = recipeId;
        this.quantity = quantity;
        this.totalAmount = totalAmount;
        this.saleTimestamp = LocalDateTime.now();
    }
}