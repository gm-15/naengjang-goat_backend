package com.naengjang_goat.inventory_system.order.repository;

import com.naengjang_goat.inventory_system.order.domain.Order;
import com.naengjang_goat.inventory_system.order.domain.OrderStatus;
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

    /**
     * 점주·기간·재료 기준 BOM 역산 총 소모량 집계.
     *
     * 계산식: SUM(orderItem.quantity × bom.requiredQuantity)
     *   - orderItem.quantity  : 해당 메뉴를 몇 인분 팔았는지
     *   - bom.requiredQuantity: 그 메뉴 1인분에 해당 재료가 얼마나 들어가는지
     *
     * 단위: RecipeBom.unit 기준 (UnitConverter로 baseUnit 변환은 서비스에서 수행).
     * CANCELLED 주문 제외.
     */
    @Query("SELECT COALESCE(SUM(CAST(oi.quantity AS java.math.BigDecimal) * b.requiredQuantity), 0) " +
           "FROM Order o " +
           "JOIN o.items oi " +
           "JOIN RecipeBom b ON b.menu.id = oi.menu.id AND b.ingredient.id = :ingredientId " +
           "WHERE o.user.id = :userId " +
           "  AND o.createdAt BETWEEN :from AND :to " +
           "  AND o.orderStatus <> :excludedStatus")
    java.math.BigDecimal sumIngredientUsage(
            @Param("userId") Long userId,
            @Param("ingredientId") Long ingredientId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("excludedStatus") OrderStatus excludedStatus
    );
}
