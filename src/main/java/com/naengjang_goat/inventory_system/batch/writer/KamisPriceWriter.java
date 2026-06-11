package com.naengjang_goat.inventory_system.batch.writer;

import com.naengjang_goat.inventory_system.analysis.domain.MarketPrice;
import com.naengjang_goat.inventory_system.analysis.repository.MarketPriceRepository;
import com.naengjang_goat.inventory_system.inventory.domain.Ingredient;
import com.naengjang_goat.inventory_system.inventory.repository.IngredientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * KAMIS MarketPrice 저장 Writer.
 *
 * sim, 2026-06-05 — kim 인수인계서 5-2 영구 해결:
 *  - Processor 가 ingredient.findByName 으로 단일 매칭 (예: demo 의 배추) 한 결과를 받아
 *  - Writer 에서 findAllByName 으로 전체 user 의 같은 이름 ingredient 모두에 복제 저장.
 *  - 중복 체크: (ingredient_id, reported_date, source="KAMIS") 기준.
 *  → 새 사용자 가입해도 KAMIS 가격 자동 매핑됨.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class KamisPriceWriter implements ItemWriter<MarketPrice> {

    private static final String SOURCE = "KAMIS";

    private final MarketPriceRepository marketPriceRepository;
    private final IngredientRepository  ingredientRepository;

    @Override
    public void write(Chunk<? extends MarketPrice> chunk) {
        int created = 0;
        int replicated = 0;
        int skipped = 0;

        for (MarketPrice mp : chunk.getItems()) {
            Ingredient orig = mp.getIngredient();
            String name = orig.getName();
            LocalDate reportedDate = mp.getReportedDate();

            // 같은 이름의 모든 ingredient (전체 user)
            List<Ingredient> sameName = ingredientRepository.findAllByName(name);

            for (Ingredient ing : sameName) {
                // 중복 체크 (ingredient_id + reported_date + source)
                if (marketPriceRepository.existsByIngredientIdAndReportedDateAndSource(
                        ing.getId(), reportedDate, SOURCE)) {
                    skipped++;
                    continue;
                }

                if (ing.getId().equals(orig.getId())) {
                    marketPriceRepository.save(mp);
                    created++;
                } else {
                    MarketPrice copy = new MarketPrice();
                    copy.setIngredient(ing);
                    copy.setRetailPrice(mp.getRetailPrice());
                    copy.setWholesalePrice(mp.getWholesalePrice());
                    copy.setUnit(mp.getUnit());
                    copy.setReportedDate(reportedDate);
                    copy.setSource(SOURCE);
                    marketPriceRepository.save(copy);
                    replicated++;
                }
            }
        }

        log.info("[KAMIS-WRITER] created={} replicated={} skipped(dup)={}",
                created, replicated, skipped);
    }
}
