package com.naengjang_goat.inventory_system.pricing.service;

import com.naengjang_goat.inventory_system.inventory.domain.Ingredient;
import com.naengjang_goat.inventory_system.inventory.repository.IngredientRepository;
import com.naengjang_goat.inventory_system.pricing.dto.ExternalLinkDto;
import com.naengjang_goat.inventory_system.pricing.dto.KamisPriceDto;
import com.naengjang_goat.inventory_system.pricing.dto.OnlinePriceDto;
import com.naengjang_goat.inventory_system.pricing.dto.PriceDetailDto;
import com.naengjang_goat.inventory_system.pricing.util.SearchUrlBuilder;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * GET /prices/{ingredientId} — UI 시안 기준 상세 화면.
 *
 * 흐름:
 *  1. ingredient 로드 (소유 검증)
 *  2. KAMIS 시세 조회 (없으면 null)
 *  3. OnlinePriceAggregator 로 네이버 + 식자재왕 가격 머지
 *  4. onlinePrices 비어있으면 IngredientMatcher.matchForIngredient 로 즉시 fallback 매칭
 *  5. 그래도 비어있으면 BR2-11 fallback CTA (externalSearchLinks) 채움
 */
@Service
@RequiredArgsConstructor
public class PriceDetailService {

    private final IngredientRepository ingredientRepository;
    private final KamisPriceCalculator kamisPriceCalculator;
    private final OnlinePriceAggregator onlinePriceAggregator;
    private final IngredientMatcher ingredientMatcher;

    @Transactional
    public PriceDetailDto getDetail(Long userId, Long ingredientId) {
        Ingredient ingredient = ingredientRepository.findById(ingredientId)
                .orElseThrow(() -> new EntityNotFoundException("ingredient not found: " + ingredientId));

        // 소유 검증
        if (!ingredient.getUser().getId().equals(userId)) {
            throw new EntityNotFoundException("ingredient not found: " + ingredientId);
        }

        KamisPriceDto kamis = kamisPriceCalculator.buildKamis(ingredientId);
        List<OnlinePriceDto> onlinePrices = onlinePriceAggregator.aggregate(ingredientId, ingredient.getName());

        // BR2-11 fallback 트리거 — 비어있으면 즉시 매칭 1회 시도
        if (onlinePrices.isEmpty()) {
            int matched = ingredientMatcher.matchForIngredient(ingredientId);
            if (matched > 0) {
                onlinePrices = onlinePriceAggregator.aggregate(ingredientId, ingredient.getName());
            }
        }

        List<ExternalLinkDto> externalSearchLinks = onlinePrices.isEmpty()
                ? buildSearchLinks(ingredient.getName())
                : List.of();

        return PriceDetailDto.builder()
                .ingredientId(ingredientId)
                .name(ingredient.getName())
                .unit(ingredient.getBaseUnit())
                .kamis(kamis)
                .onlinePrices(onlinePrices)
                .externalSearchLinks(externalSearchLinks)
                .build();
    }

    private List<ExternalLinkDto> buildSearchLinks(String name) {
        return List.of(
                ExternalLinkDto.builder()
                        .source("NAVER_SEARCH")
                        .url(SearchUrlBuilder.naverSearchUrl(name))
                        .build(),
                ExternalLinkDto.builder()
                        .source("SIKJAJAEWANG_SEARCH")
                        .url(SearchUrlBuilder.sikjajaewangSearchUrl(name))
                        .build()
        );
    }
}
