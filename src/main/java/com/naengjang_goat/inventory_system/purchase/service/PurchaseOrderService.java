package com.naengjang_goat.inventory_system.purchase.service;

import com.naengjang_goat.inventory_system.inventory.domain.Ingredient;
import com.naengjang_goat.inventory_system.inventory.repository.IngredientRepository;
import com.naengjang_goat.inventory_system.purchase.domain.PurchaseOrder;
import com.naengjang_goat.inventory_system.purchase.domain.PurchaseStatus;
import com.naengjang_goat.inventory_system.purchase.dto.PurchaseOrderRequest;
import com.naengjang_goat.inventory_system.purchase.dto.PurchaseOrderResponse;
import com.naengjang_goat.inventory_system.purchase.dto.PurchaseOrderSummaryDto;
import com.naengjang_goat.inventory_system.purchase.repository.PurchaseOrderRepository;
import com.naengjang_goat.inventory_system.user.domain.User;
import com.naengjang_goat.inventory_system.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PurchaseOrderService {

    private static final int DEFAULT_DAYS = 30;
    private static final int MAX_DAYS = 365;

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final IngredientRepository ingredientRepository;
    private final UserRepository userRepository;

    // ─── CREATE ─────────────────────────────────────────────────────────────

    @Transactional
    public PurchaseOrderResponse create(Long userId, PurchaseOrderRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("사용자 없음: " + userId));

        Ingredient ingredient = ingredientRepository.findById(request.getIngredientId())
                .orElseThrow(() -> new NoSuchElementException("재료 없음: " + request.getIngredientId()));

        // 재료가 해당 점주 소유인지 확인
        if (!ingredient.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("해당 재료에 대한 접근 권한 없음");
        }

        BigDecimal totalAmount = request.getQuantity().multiply(request.getUnitPrice());
        LocalDate orderedAt = request.getOrderedAt() != null ? request.getOrderedAt() : LocalDate.now();

        PurchaseOrder order = PurchaseOrder.builder()
                .user(user)
                .ingredient(ingredient)
                .orderedAt(orderedAt)
                .quantity(request.getQuantity())
                .baseUnit(request.getBaseUnit())
                .unitPrice(request.getUnitPrice())
                .totalAmount(totalAmount)
                .supplier(request.getSupplier())
                .memo(request.getMemo())
                .status(PurchaseStatus.CONFIRMED)
                .build();

        return PurchaseOrderResponse.from(purchaseOrderRepository.save(order));
    }

    // ─── LIST ────────────────────────────────────────────────────────────────

    public Page<PurchaseOrderResponse> list(
            Long userId,
            LocalDate from,
            LocalDate to,
            Long ingredientId,
            PurchaseStatus status,
            int page,
            int size) {

        LocalDate[] range = resolveRange(from, to);
        Pageable pageable = PageRequest.of(page, size);

        return purchaseOrderRepository
                .findFiltered(userId, range[0], range[1], ingredientId, status, pageable)
                .map(PurchaseOrderResponse::from);
    }

    // ─── SUMMARY ─────────────────────────────────────────────────────────────

    public PurchaseOrderSummaryDto summary(Long userId, LocalDate from, LocalDate to) {
        LocalDate[] range = resolveRange(from, to);
        List<PurchaseOrder> orders = purchaseOrderRepository.findForSummary(userId, range[0], range[1]);

        BigDecimal total = orders.stream()
                .map(PurchaseOrder::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, List<PurchaseOrder>> grouped = orders.stream()
                .collect(Collectors.groupingBy(o -> o.getIngredient().getName()));

        List<PurchaseOrderSummaryDto.ByIngredientDto> byIngredient = grouped.entrySet().stream()
                .map(e -> new PurchaseOrderSummaryDto.ByIngredientDto(
                        e.getKey(),
                        e.getValue().size(),
                        e.getValue().stream()
                                .map(PurchaseOrder::getTotalAmount)
                                .reduce(BigDecimal.ZERO, BigDecimal::add)
                ))
                .sorted(Comparator.comparing(PurchaseOrderSummaryDto.ByIngredientDto::totalAmount).reversed())
                .collect(Collectors.toList());

        return PurchaseOrderSummaryDto.builder()
                .totalCount(orders.size())
                .totalAmount(total)
                .byIngredient(byIngredient)
                .build();
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    /** 기간 기본값 적용 + 최대 1년 제한 → [from, to] */
    private LocalDate[] resolveRange(LocalDate from, LocalDate to) {
        LocalDate end = to != null ? to : LocalDate.now();
        LocalDate start = from != null ? from : end.minusDays(DEFAULT_DAYS);
        if (ChronoUnit.DAYS.between(start, end) > MAX_DAYS) {
            start = end.minusDays(MAX_DAYS);
        }
        return new LocalDate[]{start, end};
    }
}
