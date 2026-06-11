package com.naengjang_goat.inventory_system.batch.processor;

import com.naengjang_goat.inventory_system.analysis.domain.MarketPrice;
import com.naengjang_goat.inventory_system.batch.dto.KamisPriceDto;
import com.naengjang_goat.inventory_system.inventory.domain.Ingredient;
import com.naengjang_goat.inventory_system.inventory.repository.IngredientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Optional;

/**
 * KAMIS DTO → MarketPrice 변환.
 *
 *  - 매칭 전략: item_code 우선 → 없으면 이름 fallback
 *    item_code 가 설정된 재료는 KAMIS 품목명 변형(봄배추/고랭지배추 등)에도 안정적으로 매칭.
 *  - 매칭 실패 시 null 반환 → Spring Batch 가 해당 row 를 자동 skip.
 *  - retail_price / wholesale_price 는 KAMIS 원본 그대로 String 저장
 *    (MarketPrice 엔티티가 String 타입 유지 — KAMIS 가 "1,234" 식 콤마 포함 형식).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class KamisPriceProcessor implements ItemProcessor<KamisPriceDto, MarketPrice> {

    private final IngredientRepository ingredientRepository;

    @Override
    public MarketPrice process(KamisPriceDto dto) {
        if (dto == null || dto.getProductName() == null) {
            return null;
        }

        // 1) item_code 우선 매칭
        Optional<Ingredient> match = (dto.getItemCode() != null && !dto.getItemCode().isBlank())
                ? ingredientRepository.findByKamisItemCode(dto.getItemCode())
                : Optional.empty();

        // 2) 코드 매칭 실패 시 이름으로 fallback
        if (match.isEmpty()) {
            match = ingredientRepository.findByName(dto.getProductName());
        }

        if (match.isEmpty()) {
            log.debug("[KAMIS-PROCESSOR] no match: itemCode='{}' name='{}'",
                    dto.getItemCode(), dto.getProductName());
            return null;
        }

        log.debug("[KAMIS-PROCESSOR] matched: itemCode='{}' name='{}' → ingredient='{}'",
                dto.getItemCode(), dto.getProductName(), match.get().getName());

        MarketPrice mp = new MarketPrice();
        mp.setIngredient(match.get());
        mp.setRetailPrice(dto.getDpr1());
        mp.setWholesalePrice(dto.getDpr4());
        mp.setUnit(dto.getUnit());
        // sim, 2026-06-05 — 30일 backfill 지원: dto 의 reportedDate 우선, 없으면 어제로 fallback
        mp.setReportedDate(dto.getReportedDate() != null
                ? dto.getReportedDate()
                : LocalDate.now().minusDays(1));
        return mp;
    }
}
