package com.naengjang_goat.inventory_system.pricing.provider;

import com.naengjang_goat.inventory_system.pricing.domain.PriceRecord;
import com.naengjang_goat.inventory_system.pricing.repository.PriceRecordRepository;
import com.naengjang_goat.inventory_system.pricing.util.WeightParser;
import com.naengjang_goat.inventory_system.shopping.client.NaverShoppingClient;
import com.naengjang_goat.inventory_system.shopping.dto.NaverShoppingItemDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * 네이버 쇼핑 검색 API 기반 OnlinePriceProvider.
 *
 * On-Demand 캐싱 (plan_park_0423_02 §6):
 *  - 30분 TTL: 같은 ingredient + source 조합에 30분 이내 row 가 있으면 외부 호출 생략
 *  - 만료/없음 → 네이버 API 호출 → top 1 추출 → price_records 저장 → 반환
 *
 * 매핑 (plan_park_0423_02 §3-3):
 *  - source = "네이버_" + (category2 or "기타")
 *  - product_name = title (HTML 태그 제거됨)
 *  - price = lprice (정수 변환)
 *  - is_discount = hprice > lprice
 *  - raw_product_id = productId (NOT NULL — 추출 실패 시 row 자체 버림)
 *  - weight_grams = WeightParser.parseGrams(title) — 실패 시 null (BR2-9)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NaverOnlinePriceProvider implements OnlinePriceProvider {

    private static final String SOURCE_PREFIX = "네이버";
    private static final Duration CACHE_TTL = Duration.ofMinutes(30);
    private static final int FETCH_LIMIT = 5;

    private final NaverShoppingClient naverShoppingClient;
    private final PriceRecordRepository priceRecordRepository;

    @Override
    public String sourcePrefix() {
        return SOURCE_PREFIX;
    }

    @Override
    @Transactional
    public List<PriceRecord> fetchLatest(Long ingredientId, String ingredientName) {
        if (ingredientId == null || ingredientName == null || ingredientName.isBlank()) {
            return Collections.emptyList();
        }

        // 1. 캐시 확인 — 같은 ingredient 의 네이버 row 가 30분 이내면 그대로 사용
        Optional<PriceRecord> cached = findFreshCached(ingredientId);
        if (cached.isPresent()) {
            return List.of(cached.get());
        }

        // 2. 외부 호출 — "재료명 업소용" 으로 저가순 검색
        String query = ingredientName + " 업소용";
        List<NaverShoppingItemDto> items = naverShoppingClient.search(query, FETCH_LIMIT);
        if (items.isEmpty()) {
            log.warn("[NAVER-PROVIDER] 검색 결과 0건: ingredientId={} query={}", ingredientId, query);
            return Collections.emptyList();
        }

        // 3. 가용한 첫 row 만 저장 (sort=asc 라 가장 저렴)
        for (NaverShoppingItemDto item : items) {
            PriceRecord saved = trySaveOne(item, ingredientId);
            if (saved != null) {
                return List.of(saved);
            }
        }
        return Collections.emptyList();
    }

    private Optional<PriceRecord> findFreshCached(Long ingredientId) {
        return priceRecordRepository
                .findFirstByIngredientIdAndSourceOrderByFetchedAtDesc(ingredientId, expectedSourceForCacheLookup())
                .filter(this::isFresh);
    }

    /**
     * 캐시 조회용 source 값 — 네이버는 카테고리가 다양해서 prefix 매칭이 어려움.
     * 임시 해결: source 컬럼에 LIKE '네이버_%' 가 필요한데, 단순화 위해 첫 호출에서 사용한 source 를 직접 기억하지 않고
     * 첫 매칭 row 1건만 가져오는 방식으로 구현.
     *
     * 한계: 카테고리가 바뀌면 캐시 미스 가능. Demo 단계에서는 허용.
     * V1: source LIKE 매칭 메서드로 대체.
     */
    private String expectedSourceForCacheLookup() {
        // findFirstByIngredientIdAndSourceOrderBy... 는 정확 매칭이라 prefix 매칭 안 됨.
        // Demo 단계: 가장 흔한 카테고리 "식품" 사용. V1 에서 LIKE 쿼리 도입.
        return SOURCE_PREFIX + "_식품";
    }

    private boolean isFresh(PriceRecord record) {
        return record.getFetchedAt().isAfter(LocalDateTime.now().minus(CACHE_TTL));
    }

    /** 단일 item 을 PriceRecord 로 저장. raw_product_id NULL 또는 가격 범위 위반 시 null 반환. */
    private PriceRecord trySaveOne(NaverShoppingItemDto item, Long ingredientId) {
        if (item.getProductId() == null || item.getProductId().isBlank()) {
            return null;
        }

        Integer price = parseIntSafe(item.getLprice());
        if (price == null || price < 100 || price > 500_000) {
            return null;
        }

        boolean discount = isDiscounted(item);
        String source = SOURCE_PREFIX + "_" + safeCategory(item.getCategory2());
        Integer weightGrams = WeightParser.parseGrams(item.getTitle());

        PriceRecord record = PriceRecord.ofExternal(
                source,
                truncate(item.getTitle(), 250),
                price,
                discount,
                item.getLink(),
                item.getProductId(),
                item.getImage(),
                weightGrams
        );
        // ingredient_id 는 수집 시점에 직접 세팅 (on-demand 호출이므로 매칭 결과 있음)
        record.assignIngredient(ingredientId);

        return priceRecordRepository.save(record);
    }

    private Integer parseIntSafe(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private boolean isDiscounted(NaverShoppingItemDto item) {
        Integer hp = parseIntSafe(item.getHprice());
        Integer lp = parseIntSafe(item.getLprice());
        return hp != null && lp != null && hp > lp;
    }

    private String safeCategory(String cat) {
        return (cat == null || cat.isBlank()) ? "기타" : cat;
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) : s;
    }
}
