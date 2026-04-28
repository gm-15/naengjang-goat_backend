package com.naengjang_goat.inventory_system.inventory.repository;

import com.naengjang_goat.inventory_system.inventory.domain.InventoryBatch;
import com.naengjang_goat.inventory_system.inventory.repository.projection.IngredientStockSummary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface InventoryBatchRepository extends JpaRepository<InventoryBatch, Long> {

    // FIFO 소진 쿼리 — 유통기한 빠른 순서 (핵심)
    List<InventoryBatch> findAllByIngredientIdAndQuantityGreaterThanOrderByExpirationDateAsc(
            Long ingredientId, BigDecimal minQuantity);

    // D-N일 이내 유통기한 임박 배치 조회 (알림용)
    @Query("SELECT b FROM InventoryBatch b WHERE b.expirationDate <= :threshold AND b.quantity > 0")
    List<InventoryBatch> findExpiringBatches(@Param("threshold") LocalDate threshold);

    // 재료별 총 재고량 합산
    @Query("SELECT COALESCE(SUM(b.quantity), 0) FROM InventoryBatch b WHERE b.ingredient.id = :ingredientId AND b.quantity > 0")
    BigDecimal sumQuantityByIngredientId(@Param("ingredientId") Long ingredientId);

    // 점주별 전체 배치 조회
    @Query("SELECT b FROM InventoryBatch b JOIN FETCH b.ingredient i WHERE i.user.id = :userId AND b.quantity > 0 ORDER BY b.expirationDate ASC")
    List<InventoryBatch> findAllByUserIdWithFetch(@Param("userId") Long userId);

    // 점주별 재료별 총 재고 집계 — UC-CORE-1 (재고 부족 Top5) 전용
    // quantity > 0 배치만 합산. warningThreshold 비교는 서비스 레이어에서 수행.
    @Query("SELECT b.ingredient.id AS ingredientId, COALESCE(SUM(b.quantity), 0) AS totalQuantity " +
           "FROM InventoryBatch b " +
           "WHERE b.ingredient.user.id = :userId AND b.quantity > 0 " +
           "GROUP BY b.ingredient.id")
    List<IngredientStockSummary> findStockSummaryByUserId(@Param("userId") Long userId);

    // PESSIMISTIC LOCK 버전 — PessimisticLockStrategy 전용
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM InventoryBatch b WHERE b.ingredient.id = :ingredientId AND b.quantity > 0 ORDER BY b.expirationDate ASC")
    List<InventoryBatch> findAllByIngredientIdWithPessimisticLock(@Param("ingredientId") Long ingredientId);
}
