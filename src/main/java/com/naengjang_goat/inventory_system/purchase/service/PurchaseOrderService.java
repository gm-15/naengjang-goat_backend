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
    private final com.naengjang_goat.inventory_system.global.util.UnitConverter units;
    private final java.time.Clock clock;

    // ─── CREATE ─────────────────────────────────────────────────────────────

    @Transactional
    public PurchaseOrderResponse create(Long userId, PurchaseOrderRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("사용자 없음: " + userId));

        Ingredient ingredient = ingredientRepository.findById(request.getIngredientId())
                .orElseThrow(() -> new NoSuchElementException("재료 없음: " + request.getIngredientId()));

        // 재료가 해당 점주 소유인지 확인
        if (!ingredient.getUser().getId().equals(userId)) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN,"해당 재료에 대한 접근 권한 없음");
        }

        BigDecimal expected;
        try {
            if ("포장".equals(request.getBaseUnit())) {
                if(request.getPackageSize()==null || request.getPackageSize().signum()<=0 || request.getPackageUnit()==null
                    || request.getQuantity().stripTrailingZeros().scale()>0) throw new IllegalArgumentException("포장 수량은 정수, 포장당 크기와 단위는 필수입니다");
                expected=units.convert(request.getPackageUnit(),request.getPackageSize().multiply(request.getQuantity()),ingredient.getBaseUnit());
            } else expected=units.convert(request.getBaseUnit(),request.getQuantity(),ingredient.getBaseUnit());
            if(expected.signum()<=0 || expected.compareTo(new BigDecimal("9999999.999"))>0) throw new IllegalArgumentException("재고 환산 수량 범위를 확인하세요");
            if(request.getSourceUrl()!=null && !request.getSourceUrl().isBlank()) {
                var uri=java.net.URI.create(request.getSourceUrl());
                if(!java.util.Set.of("https","http").contains(uri.getScheme()) || uri.getHost()==null) throw new IllegalArgumentException("상품 링크 형식을 확인하세요");
            }
        } catch(IllegalArgumentException e) {throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,e.getMessage());}
        BigDecimal totalAmount = request.getQuantity().multiply(request.getUnitPrice());
        if(totalAmount.compareTo(new BigDecimal("9999999999.99"))>0) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,"총금액 범위 초과");
        LocalDate orderedAt = request.getOrderedAt() != null ? request.getOrderedAt() : LocalDate.now(clock);

        if(orderedAt.isAfter(LocalDate.now(clock))) throw new IllegalArgumentException("미래 주문일은 등록할 수 없습니다");

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
                .productName(request.getProductName()!=null ? request.getProductName() : ingredient.getName())
                .sourceUrl(request.getSourceUrl()).expectedQuantityBase(expected).inventoryUnit(ingredient.getBaseUnit())
                .deliveryStatus("WAITING")
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
            String deliveryStatus,
            int page,
            int size) {

        LocalDate[] range = resolveRange(from, to);
        if(page<0 || size<1 || size>100) throw new IllegalArgumentException("페이지/크기 범위를 확인하세요");
        if(deliveryStatus!=null && !java.util.Set.of("WAITING","RECEIVED","LEGACY").contains(deliveryStatus)) throw new IllegalArgumentException("배송 상태를 확인하세요");
        Pageable pageable = PageRequest.of(page, size);

        return purchaseOrderRepository
                .findFiltered(userId, range[0], range[1], ingredientId, status, deliveryStatus, pageable)
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
        LocalDate end = to != null ? to : LocalDate.now(clock);
        LocalDate start = from != null ? from : end.minusDays(DEFAULT_DAYS);
        if(start.isAfter(end)) throw new IllegalArgumentException("조회 시작일은 종료일 이하여야 합니다");
        if (ChronoUnit.DAYS.between(start, end) > MAX_DAYS) {
            start = end.minusDays(MAX_DAYS);
        }
        return new LocalDate[]{start, end};
    }
}
