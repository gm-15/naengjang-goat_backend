package com.naengjang_goat.inventory_system.order.repository;

import com.naengjang_goat.inventory_system.order.domain.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {

    // 점주별 + 기간별 주문 조회 (판매 이력 화면용)
    @Query("SELECT o FROM Order o JOIN FETCH o.items i JOIN FETCH i.menu WHERE o.user.id = :userId AND o.createdAt BETWEEN :from AND :to")
    List<Order> findAllByUserIdAndCreatedAtBetween(
            @Param("userId") Long userId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );
}
