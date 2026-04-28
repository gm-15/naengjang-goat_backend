package com.naengjang_goat.inventory_system.pricing.service;

import com.naengjang_goat.inventory_system.inventory.domain.Ingredient;
import com.naengjang_goat.inventory_system.inventory.repository.IngredientRepository;
import com.naengjang_goat.inventory_system.pricing.dto.ExternalLinkDto;
import com.naengjang_goat.inventory_system.pricing.dto.KamisPriceDto;
import com.naengjang_goat.inventory_system.pricing.dto.LowestTopItemDto;
import com.naengjang_goat.inventory_system.pricing.dto.TrendDto;
import com.naengjang_goat.inventory_system.pricing.util.SearchUrlBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * GET /prices/lowest-top — 사장님 등록 재료 중 KAMIS 하락률 Top N.
 *
 * 정책 (UC-CORE-2 BR2-2):
 *  - 정렬: 한 주 평균가 대비 현재 하락률 내림차순
 *  - 표시 범위: 점주 등록 재료만 (BR2-3)
 *  - KAMIS 데이터 없는 재료도 검색 URL 만 채워서 반환 (Tier 2 항상 노출)
 */
@Service
@RequiredArgsConstructor
public class LowestTopService {

    private static final int DEFAULT_LIMIT = 5;

    private final IngredientRepository ingredientRepository;
    private final KamisPriceCalculator kamisPriceCalculator;

    @Transactional(readOnly = true)
    public List<LowestTopItemDto> getLowestTop(Long userId, int limit) {
        if (limit <= 0) limit = DEFAULT_LIMIT;

        List<Ingredient> ingredients = ingredientRepository.findAllByUserIdWithFetch(userId);
        if (ingredients.isEmpty()) {
            return List.of();
        }

        List<LowestTopItemDto> built = new ArrayList<>(ingredients.size());
        for (Ingredient ing : ingredients) {
            built.add(buildItem(ing));
        }

        // 하락률 내림차순. null 은 끝으로.
        built.sort(Comparator.comparing(
                LowestTopItemDto::getDropRatePct,
                Comparator.nullsLast(Comparator.reverseOrder())
        ));

        return built.size() > limit ? built.subList(0, limit) : built;
    }

    private LowestTopItemDto buildItem(Ingredient ingredient) {
        KamisPriceDto kamis = kamisPriceCalculator.buildKamis(ingredient.getId());
        TrendDto trend = kamisPriceCalculator.buildTrend(ingredient.getId());

        Double dropRate = kamis != null
                ? kamisPriceCalculator.dropRatePct(kamis.getWeekAvg(), kamis.getCurrentPricePerKg())
                : null;

        return LowestTopItemDto.builder()
                .ingredientId(ingredient.getId())
                .name(ingredient.getName())
                .weekAvg(kamis != null ? kamis.getWeekAvg() : null)
                .monthAvg(kamis != null ? kamis.getMonthAvg() : null)
                .todayPrice(kamis != null ? kamis.getCurrentPricePerKg() : null)
                .dropRatePct(dropRate)
                .trend(trend)
                .externalLinks(buildExternalLinks(ingredient.getName()))
                .build();
    }

    private List<ExternalLinkDto> buildExternalLinks(String name) {
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
