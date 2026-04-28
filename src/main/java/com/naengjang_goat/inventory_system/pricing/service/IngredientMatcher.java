package com.naengjang_goat.inventory_system.pricing.service;

import com.naengjang_goat.inventory_system.inventory.domain.Ingredient;
import com.naengjang_goat.inventory_system.inventory.repository.IngredientRepository;
import com.naengjang_goat.inventory_system.pricing.domain.PriceRecord;
import com.naengjang_goat.inventory_system.pricing.repository.PriceRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * price_records.product_name 을 Ingredient.name 과 매칭해
 * ingredient_id 를 채워 넣는 배치 + on-demand 매처.
 *
 * 근거:
 *  - sim 합의 응답 §추가 작업 분담: 크롤러 도메인 의존 분리, 백엔드 배치로 운영
 *  - plan_park_0426_01 §3 Q3 응답: 10분 주기 + on-demand fallback
 *
 * 매칭 정책 (Demo):
 *  - product_name 이 ingredient.name 을 부분 문자열로 포함하면 매칭
 *  - 같은 user 범위 내에서만 유효 (V1: 별칭 테이블, V2: 임베딩)
 *  - 전체 사용자 범위 매칭은 비용 크므로, Demo 는 1명 사용자 가정 (MockAuthFilter)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IngredientMatcher {

    /** 미매칭 row 를 거슬러 올라갈 최대 일수 (오래된 건 무의미) */
    private static final int LOOKBACK_DAYS = 7;

    private final PriceRecordRepository priceRecordRepository;
    private final IngredientRepository ingredientRepository;

    /**
     * 10분마다 미매칭 row 일괄 매칭.
     * 매칭 안 된 row 가 많아도 부담 적은 LIKE 단순 매칭이라 충분.
     */
    @Scheduled(fixedDelay = 10 * 60 * 1000L, initialDelay = 60 * 1000L)
    @Transactional
    public void matchUnmatchedBatch() {
        LocalDateTime since = LocalDateTime.now().minusDays(LOOKBACK_DAYS);
        List<PriceRecord> unmatched = priceRecordRepository.findUnmatched(since);

        if (unmatched.isEmpty()) {
            return;
        }

        // 모든 ingredient 1회 로드 (사용자 수 적은 Demo 가정)
        List<Ingredient> allIngredients = ingredientRepository.findAll();
        if (allIngredients.isEmpty()) {
            log.debug("[MATCHER] Ingredient 등록 0건 — 매칭 스킵");
            return;
        }

        int matchedCount = 0;
        for (PriceRecord pr : unmatched) {
            Long matchedId = matchOne(pr.getProductName(), allIngredients);
            if (matchedId != null) {
                pr.assignIngredient(matchedId);
                matchedCount++;
            }
        }

        log.info("[MATCHER] 일괄 매칭 완료: {}/{} 매칭", matchedCount, unmatched.size());
    }

    /**
     * On-demand 매칭 — 상세 API 진입 시 fallback.
     * 배치 주기(10분) 기다리지 못할 경우 즉시 매칭 시도.
     */
    @Transactional
    public int matchForIngredient(Long ingredientId) {
        Ingredient ingredient = ingredientRepository.findById(ingredientId).orElse(null);
        if (ingredient == null) {
            return 0;
        }

        LocalDateTime since = LocalDateTime.now().minusDays(LOOKBACK_DAYS);
        List<PriceRecord> unmatched = priceRecordRepository.findUnmatched(since);

        int matched = 0;
        for (PriceRecord pr : unmatched) {
            if (containsIgnoreCase(pr.getProductName(), ingredient.getName())) {
                pr.assignIngredient(ingredientId);
                matched++;
            }
        }
        return matched;
    }

    /** 단일 row 매칭 — 부분 문자열 매칭 (LIKE 동등). 가장 먼저 매칭된 ingredient 사용. */
    private Long matchOne(String productName, List<Ingredient> ingredients) {
        if (productName == null) return null;
        for (Ingredient ing : ingredients) {
            if (containsIgnoreCase(productName, ing.getName())) {
                return ing.getId();
            }
        }
        return null;
    }

    private boolean containsIgnoreCase(String haystack, String needle) {
        if (haystack == null || needle == null || needle.isBlank()) return false;
        return haystack.toLowerCase().contains(needle.toLowerCase());
    }
}
