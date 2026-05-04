package com.naengjang_goat.inventory_system.batch.ekape;

import com.naengjang_goat.inventory_system.analysis.domain.MarketPrice;
import com.naengjang_goat.inventory_system.analysis.repository.MarketPriceRepository;
import com.naengjang_goat.inventory_system.batch.ekape.EkapeApiClient.EkapeProduct;
import com.naengjang_goat.inventory_system.inventory.domain.Ingredient;
import com.naengjang_goat.inventory_system.inventory.repository.IngredientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * EKAPE 축산물 소비자가격 일별 수집 스케줄러.
 *
 * 실행 시점: 매일 03:30 KST (KAMIS 배치 03:00 이후)
 *
 * 동작:
 *   1) kamisCategory = 'LIVESTOCK' 인 재료 전체 조회
 *   2) 재료명으로 EKAPE 파라미터 매핑
 *   3) 최근 31일 일별 데이터 조회 (backfill + 최신 유지)
 *   4) 미존재 날짜만 market_price 에 삽입 (source='EKAPE', 멱등)
 *
 * 가격 단위:
 *   소·돼지 = 원/100g → retailPrice 저장, wholesalePrice = null
 *   닭 육계 = 원/kg   → 동일
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EkapeScheduler {

    private static final String SOURCE = "EKAPE";
    /** 최근 N일 데이터 유지 (첫 실행 시 backfill 포함). */
    private static final int FETCH_DAYS = 31;

    private final EkapeApiClient ekapeApiClient;
    private final IngredientRepository ingredientRepository;
    private final MarketPriceRepository marketPriceRepository;

    @Scheduled(cron = "0 30 3 * * *")
    public void collect() {
        log.info("[EKAPE-SCHEDULER] 축산물 가격 수집 시작");

        List<Ingredient> livestockIngredients =
                ingredientRepository.findByKamisCategory("LIVESTOCK");

        if (livestockIngredients.isEmpty()) {
            log.info("[EKAPE-SCHEDULER] LIVESTOCK 재료 없음 — 종료");
            return;
        }

        LocalDate today = LocalDate.now();
        LocalDate from  = today.minusDays(FETCH_DAYS);

        int totalSaved = 0;
        for (Ingredient ingredient : livestockIngredients) {
            totalSaved += processIngredient(ingredient, from, today);
        }

        log.info("[EKAPE-SCHEDULER] 완료 — 총 {}건 저장", totalSaved);
    }

    /** 단일 재료 처리. 저장된 행 수 반환. */
    private int processIngredient(Ingredient ingredient, LocalDate from, LocalDate to) {
        Optional<EkapeProduct> productOpt = ekapeApiClient.resolveProduct(ingredient.getName());
        if (productOpt.isEmpty()) {
            log.debug("[EKAPE-SCHEDULER] 매핑 없음: '{}'", ingredient.getName());
            return 0;
        }

        EkapeProduct product = productOpt.get();
        Map<LocalDate, Integer> prices = ekapeApiClient.fetchDailyPrices(product, from, to);

        int saved = 0;
        for (Map.Entry<LocalDate, Integer> entry : prices.entrySet()) {
            LocalDate date  = entry.getKey();
            Integer   price = entry.getValue();

            if (marketPriceRepository.existsByIngredientIdAndReportedDateAndSource(
                    ingredient.getId(), date, SOURCE)) {
                continue; // 이미 존재 → 스킵
            }

            MarketPrice mp = new MarketPrice();
            mp.setIngredient(ingredient);
            mp.setRetailPrice(String.valueOf(price));   // 소비자가격 = 소매가 대응
            mp.setWholesalePrice(null);                 // EKAPE는 도매가 미제공
            mp.setUnit(product.unit());
            mp.setReportedDate(date);
            mp.setSource(SOURCE);

            marketPriceRepository.save(mp);
            saved++;
        }

        if (saved > 0) {
            log.info("[EKAPE-SCHEDULER] '{}' → {} ({}) {}건 저장",
                    ingredient.getName(), product.name(), product.unit(), saved);
        }
        return saved;
    }
}
