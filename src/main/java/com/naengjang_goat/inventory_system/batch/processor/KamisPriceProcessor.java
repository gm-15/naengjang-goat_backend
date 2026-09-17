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
import java.util.regex.Pattern;

/**
 * KAMIS DTO → MarketPrice 변환.
 *
 *  - 매칭 전략: item_code 우선 → 없으면 이름 fallback
 *    item_code 가 설정된 재료는 KAMIS 품목명 변형(봄배추/고랭지배추 등)에도 안정적으로 매칭.
 *  - 매칭 실패 시 null 반환 → Spring Batch 가 해당 row 를 자동 skip.
 *  - 가격은 dpr1(조회일 당일 도매가)을 wholesale_price 에 String 저장하고 retail_price 는 비운다
 *    (MarketPrice 엔티티가 String 타입 유지 — KAMIS 가 "1,234" 식 콤마 포함 형식).
 *  - kg 단위 품목은 unit 을 "1kg" 로 저장한다 (normalizeUnit 참고).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class KamisPriceProcessor implements ItemProcessor<KamisPriceDto, MarketPrice> {

    /** 숫자 + kg 이 들어간 단위. p_convert_kg_yn=Y 에서 KAMIS 가 원/kg 로 환산하는 대상. */
    private static final Pattern KG_UNIT = Pattern.compile("\\d+(\\.\\d+)?\\s*kg", Pattern.CASE_INSENSITIVE);

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
        // park, 2026-09-17 — 저장 규칙 정정
        //  · 가격: dpr1(조회일 당일). 기존 dpr4 는 2주일 전 가격이었음
        //  · retail: 비움. p_product_cls_code=02(도매) 호출이라 소매가가 없고, 읽는 쪽은 wholesale 우선
        //  · unit: normalizeUnit — 원본 unit 을 두면 toPricePerKg 가 한 번 더 나눠 이중 환산됨
        mp.setWholesalePrice(dto.getDpr1());
        mp.setRetailPrice(null);
        mp.setUnit(normalizeUnit(dto.getUnit()));
        // sim, 2026-06-05 — 30일 backfill 지원: dto 의 reportedDate 우선, 없으면 어제로 fallback
        mp.setReportedDate(dto.getReportedDate() != null
                ? dto.getReportedDate()
                : LocalDate.now().minusDays(1));
        return mp;
    }

    /**
     * kg 단위("10kg(그물망 3포기)" 등)는 KAMIS 가 이미 원/kg 로 환산해 주므로 "1kg" 로 바꾼다.
     * unit 필드는 환산 후에도 원본 문자열로 내려오기 때문에 그대로 저장하면
     * KamisPriceCalculator.toPricePerKg 가 10 으로 한 번 더 나눈다 (배추 1,923원/kg → 192원/kg).
     * 개·구·마리·속·L 단위는 KAMIS 도 환산하지 않으므로 원본을 유지한다.
     * 읽는 쪽 toPricePerKg 는 EKAPE("100g") 도 함께 쓰므로 저장 쪽에서만 처리한다.
     */
    private String normalizeUnit(String unit) {
        return (unit != null && KG_UNIT.matcher(unit).find()) ? "1kg" : unit;
    }
}
