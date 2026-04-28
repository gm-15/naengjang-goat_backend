package com.naengjang_goat.inventory_system.pricing.service;

import com.naengjang_goat.inventory_system.pricing.domain.PriceRecord;
import com.naengjang_goat.inventory_system.pricing.dto.OnlinePriceDto;
import com.naengjang_goat.inventory_system.pricing.provider.NaverOnlinePriceProvider;
import com.naengjang_goat.inventory_system.pricing.repository.PriceRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 여러 소스의 최신 PriceRecord 를 OnlinePriceDto 리스트로 머지하고 isLowest 마크.
 *
 * 흐름 (plan_park_0423_02 §5):
 *  1. 네이버는 On-Demand 로 호출/저장 (캐시 30분)
 *  2. 식자재왕은 팀원 크롤러가 별도 적재 → DB 조회만
 *  3. ingredient_id = 입력 ID 인 모든 source 의 최신 row 조회
 *  4. weight_grams / unit_price_per_kg 누락 row 제외 (BR2-9)
 *  5. unit_price_per_kg 최솟값에 isLowest=true (동률 시 복수)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OnlinePriceAggregator {

    private final PriceRecordRepository priceRecordRepository;
    private final NaverOnlinePriceProvider naverOnlinePriceProvider;

    /**
     * 해당 ingredient 의 모든 소스 최신 가격을 OnlinePriceDto 리스트로.
     *
     * @param ingredientId   ingredient.id (NULL 이면 빈 리스트)
     * @param ingredientName ingredient.name (네이버 검색 쿼리용)
     * @return weightGrams 파싱 성공한 row 만, unit_price_per_kg 오름차순
     */
    public List<OnlinePriceDto> aggregate(Long ingredientId, String ingredientName) {
        if (ingredientId == null) {
            return List.of();
        }

        // 1. 네이버 On-Demand — 캐시 만료 시 외부 호출 + 저장 (트랜잭션 내부)
        try {
            naverOnlinePriceProvider.fetchLatest(ingredientId, ingredientName);
        } catch (Exception e) {
            log.warn("[AGGREGATOR] 네이버 호출 실패 (캐시 또는 빈 응답으로 fallback): {}", e.getMessage());
        }

        // 2. DB 에서 모든 source 의 최신 row 1개씩 조회 (네이버 + 식자재왕 + 그 외)
        List<PriceRecord> rows = priceRecordRepository.findLatestByIngredientPerSource(ingredientId);
        if (rows.isEmpty()) {
            return List.of();
        }

        // 3. weight_grams / unit_price_per_kg 누락 row 제외 (BR2-9)
        List<PriceRecord> usable = rows.stream()
                .filter(r -> r.getWeightGrams() != null
                        && r.getUnitPricePerKg() != null
                        && r.getUnitPricePerKg() > 0)
                .toList();
        if (usable.isEmpty()) {
            return List.of();
        }

        // 4. 최저가 판정
        long minUnit = usable.stream()
                .mapToLong(PriceRecord::getUnitPricePerKg)
                .min()
                .orElse(Long.MAX_VALUE);

        // 5. DTO 변환 + isLowest 표시 + unitPricePerKg 오름차순
        List<OnlinePriceDto> dtos = new ArrayList<>(usable.size());
        for (PriceRecord r : usable) {
            dtos.add(OnlinePriceDto.builder()
                    .source(r.getSource())
                    .sourceLabel(extractLabel(r.getSource()))
                    .productName(r.getProductName())
                    .productUrl(r.getProductUrl())
                    .imageUrl(r.getImageUrl())
                    .price(r.getPrice())
                    .currency(r.getCurrency())
                    .isDiscount(r.isDiscount())
                    .weightGrams(r.getWeightGrams())
                    .unitPricePerKg(r.getUnitPricePerKg())
                    .isLowest(r.getUnitPricePerKg() == minUnit)
                    .fetchedAt(r.getFetchedAt())
                    .build());
        }
        dtos.sort(Comparator.comparing(OnlinePriceDto::getUnitPricePerKg));
        return dtos;
    }

    /** "식자재왕_채소/과일" → "식자재왕". UI 표기용. */
    private String extractLabel(String source) {
        if (source == null) return "";
        int idx = source.indexOf('_');
        return idx > 0 ? source.substring(0, idx) : source;
    }
}
