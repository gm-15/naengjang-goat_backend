package com.naengjang_goat.inventory_system.pricing.repository;

import com.naengjang_goat.inventory_system.pricing.domain.PriceRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * price_records 조회·저장 리포지토리.
 *
 * 핵심 쿼리:
 *  - findLatestByIngredientPerSource: 상세 화면용 — ingredient_id 별 소스마다 가장 최신 1건씩
 *  - findRecentForFreshness: 캐시 TTL 체크 (NaverOnlinePriceProvider On-Demand 갱신 판정)
 *  - findUnmatched: 매칭 배치(IngredientMatcher) 가 ingredient_id NULL 인 row 조회
 */
public interface PriceRecordRepository extends JpaRepository<PriceRecord, Long> {

    /**
     * 해당 ingredient 의 최신 가격 row 들 (소스별 1건).
     * onlinePrices 응답 빌드에 사용.
     *
     * 같은 source 의 여러 row 중에서는 fetched_at 가장 최근 1건만.
     * weight_grams IS NULL 또는 ingredient_id IS NULL row 는 제외.
     */
    @Query(value = """
            SELECT pr.*
              FROM price_records pr
             INNER JOIN (
                   SELECT source, MAX(fetched_at) AS max_fetched
                     FROM price_records
                    WHERE ingredient_id = :ingredientId
                      AND weight_grams IS NOT NULL
                    GROUP BY source
             ) latest
                ON pr.source = latest.source
               AND pr.fetched_at = latest.max_fetched
               AND pr.ingredient_id = :ingredientId
             ORDER BY pr.unit_price_per_kg ASC
            """, nativeQuery = true)
    List<PriceRecord> findLatestByIngredientPerSource(@Param("ingredientId") Long ingredientId);

    /**
     * 특정 source 의 최신 row 1건 (TTL 체크용).
     * NaverOnlinePriceProvider 가 "30분 이내 데이터 있는지" 확인할 때 사용.
     */
    Optional<PriceRecord> findFirstByIngredientIdAndSourceOrderByFetchedAtDesc(
            Long ingredientId, String source);

    /**
     * 매칭 안 된 row 들 — IngredientMatcher 배치가 처리 대상으로 조회.
     * 너무 오래된 건 무의미하므로 최근 N일 이내 row 만.
     */
    @Query("""
            SELECT pr FROM PriceRecord pr
             WHERE pr.ingredientId IS NULL
               AND pr.fetchedAt >= :since
             ORDER BY pr.fetchedAt DESC
            """)
    List<PriceRecord> findUnmatched(@Param("since") LocalDateTime since);
}
