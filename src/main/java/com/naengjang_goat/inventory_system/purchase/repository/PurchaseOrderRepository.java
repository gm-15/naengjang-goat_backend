package com.naengjang_goat.inventory_system.purchase.repository;

import com.naengjang_goat.inventory_system.purchase.domain.PurchaseOrder;
import com.naengjang_goat.inventory_system.purchase.domain.PurchaseStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {

    /**
     * 발주 목록 조회 — 점주 기준, 기간·재료·상태 선택 필터.
     * null 파라미터는 필터 미적용 (전체 포함).
     */
    @Query("SELECT po FROM PurchaseOrder po " +
            "JOIN FETCH po.ingredient " +
            "WHERE po.user.id = :userId " +
            "AND po.orderedAt BETWEEN :from AND :to " +
            "AND (:ingredientId IS NULL OR po.ingredient.id = :ingredientId) " +
            "AND (:status IS NULL OR po.status = :status) " +
            "ORDER BY po.orderedAt DESC")
    Page<PurchaseOrder> findFiltered(
            @Param("userId") Long userId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("ingredientId") Long ingredientId,
            @Param("status") PurchaseStatus status,
            Pageable pageable);

    /**
     * 발주 요약용 — CANCELLED 제외, 기간 내 전체 목록 (집계용).
     */
    @Query("SELECT po FROM PurchaseOrder po " +
            "JOIN FETCH po.ingredient " +
            "WHERE po.user.id = :userId " +
            "AND po.orderedAt BETWEEN :from AND :to " +
            "AND po.status <> com.naengjang_goat.inventory_system.purchase.domain.PurchaseStatus.CANCELLED " +
            "ORDER BY po.orderedAt DESC")
    List<PurchaseOrder> findForSummary(
            @Param("userId") Long userId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);
}
