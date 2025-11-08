package com.naengjang_goat.inventory_system.inventory.domain;

import com.naengjang_goat.inventory_system.recipe.domain.Recipe; // recipe 패키지의 Recipe 참조
import com.naengjang_goat.inventory_system.user.domain.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;

/**
 * '판매 기록'을 저장하는 엔티티
 * '토마토 스파게티'가 '2개' 팔렸다.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class SaleHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private double totalAmount;

    // 어떤 메뉴(Recipe)가 팔렸는지
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipe_id")
    private Recipe recipe;

    @Column(nullable = false)
    private Integer quantitySold; // 판매 수량

    @Column(nullable = false)
    private LocalDateTime saleTimestamp; // 판매 시각

    // ✅ 점주와 N:1 관계 설정
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;
}
