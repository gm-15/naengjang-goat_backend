package com.naengjang_goat.inventory_system.pricing.service;

import com.naengjang_goat.inventory_system.inventory.domain.Ingredient;
import com.naengjang_goat.inventory_system.inventory.repository.IngredientRepository;
import com.naengjang_goat.inventory_system.pricing.dto.KamisPriceDto;
import com.naengjang_goat.inventory_system.pricing.dto.OnlinePriceDto;
import com.naengjang_goat.inventory_system.pricing.dto.PriceDetailViewDto;
import com.naengjang_goat.inventory_system.pricing.dto.PricePointItemDto;
import com.naengjang_goat.inventory_system.pricing.dto.PriceSourceItemDto;
import com.naengjang_goat.inventory_system.pricing.dto.PriceTrendResponse;
import com.naengjang_goat.inventory_system.pricing.dto.TrendPointDto;
import com.naengjang_goat.inventory_system.pricing.util.SourceLogoRegistry;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;

/**
 * GET /prices/{ingredientId} — 시안 {@code /lowest-price/{id}} 화면 데이터.
 *
 * v3 (2026-06-01, sim):
 *  - 응답을 {@link PriceDetailViewDto} 로 교체.
 *  - 프론트(kim) {@code ProductData} 타입과 1:1 매핑.
 *  - {@link PriceTrendService} 호출해 priceHistory 30개 포인트 응답에 포함 (1번 호출).
 *  - {@link SourceLogoRegistry} 로 채널 로고 URL 부착.
 *
 * 흐름:
 *  1. ingredient 로드 + 소유 검증
 *  2. KAMIS 시세
 *  3. OnlinePriceAggregator (네이버 + 식자재왕)
 *  4. BR2-11 fallback (비어있으면 매칭 1회 시도)
 *  5. PriceTrendService → 30일 추이
 *  6. ViewDto 어댑터 매핑
 */
@Service
@RequiredArgsConstructor
public class PriceDetailService {

    private static final int TREND_DAYS = 30;
    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;

    private final IngredientRepository ingredientRepository;
    private final KamisPriceCalculator kamisPriceCalculator;
    private final OnlinePriceAggregator onlinePriceAggregator;
    private final IngredientMatcher ingredientMatcher;
    private final PriceTrendService priceTrendService;

    @Transactional
    public PriceDetailViewDto getDetail(Long userId, Long ingredientId) {
        Ingredient ingredient = ingredientRepository.findById(ingredientId)
                .orElseThrow(() -> new EntityNotFoundException("ingredient not found: " + ingredientId));

        // 소유 검증
        if (!ingredient.getUser().getId().equals(userId)) {
            throw new EntityNotFoundException("ingredient not found: " + ingredientId);
        }

        // 1. KAMIS 시세
        KamisPriceDto kamis = kamisPriceCalculator.buildKamis(ingredientId);

        // 2. 온라인 채널 가격 (네이버 + 식자재왕)
        List<OnlinePriceDto> onlinePrices = onlinePriceAggregator.aggregate(ingredientId, ingredient.getName());

        // 3. BR2-11 fallback — 비어있으면 즉시 매칭 시도
        if (onlinePrices.isEmpty()) {
            int matched = ingredientMatcher.matchForIngredient(ingredientId);
            if (matched > 0) {
                onlinePrices = onlinePriceAggregator.aggregate(ingredientId, ingredient.getName());
            }
        }

        // 4. 30일 가격 추이
        PriceTrendResponse trend = safeFetchTrend(ingredientId);

        // 5. ViewDto 매핑
        return toViewDto(ingredient, kamis, onlinePrices, trend);
    }

    // ─── private mappers ────────────────────────────────────────────────────

    /** trend 조회 실패해도 상세 응답은 정상 반환 (priceHistory만 비움). */
    private PriceTrendResponse safeFetchTrend(Long ingredientId) {
        try {
            return priceTrendService.getTrend(ingredientId, TREND_DAYS);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private PriceDetailViewDto toViewDto(Ingredient ingredient,
                                         KamisPriceDto kamis,
                                         List<OnlinePriceDto> onlinePrices,
                                         PriceTrendResponse trend) {
        return PriceDetailViewDto.builder()
                .id(ingredient.getId())
                .name(ingredient.getName())
                .category(ingredient.getCategory())          // 신규 필드 (Ingredient 확장)
                .image(ingredient.getImageUrl())             // 신규 필드 (Ingredient 확장)
                .unit(ingredient.getBaseUnit())
                .kamisPrice(kamis != null ? kamis.getCurrentPricePerKg() : null)
                .kamisDate(kamis != null && kamis.getPriceDate() != null
                        ? kamis.getPriceDate().format(ISO_DATE)
                        : null)
                .sources(mapSources(onlinePrices))
                .priceHistory(mapPriceHistory(trend))
                .build();
    }

    private List<PriceSourceItemDto> mapSources(List<OnlinePriceDto> onlinePrices) {
        if (onlinePrices == null || onlinePrices.isEmpty()) {
            return Collections.emptyList();
        }
        return onlinePrices.stream()
                .map(op -> PriceSourceItemDto.builder()
                        .platform(op.getSourceLabel())
                        .price(op.getPrice())
                        .url(op.getProductUrl())
                        .logo(SourceLogoRegistry.resolve(op.getSourceLabel()))
                        .isLowest(op.isLowest())
                        .build())
                .toList();
    }

    private List<PricePointItemDto> mapPriceHistory(PriceTrendResponse trend) {
        if (trend == null || trend.getPoints() == null || trend.getPoints().isEmpty()) {
            return Collections.emptyList();
        }
        return trend.getPoints().stream()
                .map(this::mapPoint)
                .toList();
    }

    private PricePointItemDto mapPoint(TrendPointDto p) {
        Long price = p.wholesalePrice() != null ? p.wholesalePrice() : p.retailPrice();
        return PricePointItemDto.builder()
                .date(p.date() != null ? p.date().format(ISO_DATE) : null)
                .price(price)
                .build();
    }
}
