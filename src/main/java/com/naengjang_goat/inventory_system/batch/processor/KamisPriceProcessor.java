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
import java.util.List;

/**
 * KAMIS DTO → MarketPrice 변환.
 *
 *  - 매칭 전략: item_code 우선 → 없으면 이름 fallback (모든 user Ingredient).
 *  - sim, 2026-10-12 — Spring Batch chunk 트랜잭션 안에서 save 를 호출하면 Writer skip 흐름에서
 *    rollback 되어 저장이 유실되는 현상 확인. 저장은 {@link MarketPriceSaveService} 가
 *    REQUIRES_NEW 로 열어 별도 commit 하도록 분리. Processor 는 null 을 반환해 Writer 를 skip.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class KamisPriceProcessor implements ItemProcessor<KamisPriceDto, MarketPrice> {

    private final IngredientRepository ingredientRepository;
    private final MarketPriceSaveService marketPriceSaveService;

    @Override
    public MarketPrice process(KamisPriceDto dto) {
        if (dto == null || dto.getProductName() == null) {
            return null;
        }

        // 1) item_code 우선 매칭 (모든 user Ingredient)
        List<Ingredient> matches = (dto.getItemCode() != null && !dto.getItemCode().isBlank())
                ? ingredientRepository.findAllByKamisItemCode(dto.getItemCode())
                : List.of();

        // 2) 코드 매칭 실패 시 이름으로 fallback
        if (matches.isEmpty()) {
            matches = ingredientRepository.findAllByName(dto.getProductName());
        }

        if (matches.isEmpty()) {
            log.debug("[KAMIS-PROCESSOR] no match: itemCode='{}' name='{}'",
                    dto.getItemCode(), dto.getProductName());
            return null;
        }

        // sim, 2026-10-12 — KAMIS 는 요청일 당일 데이터를 밤에 업로드하므로 조회 시점에
        //   dpr1(당일)이 "-"(= null) 로 올 때가 많다. 그런 경우 dpr2(1일전) 로 fallback 하고
        //   reportedDate 도 하루 전으로 보정해 "실측 날짜 ↔ 가격" 정합성을 유지한다.
        String retailPrice = dto.getDpr1();
        LocalDate reportedDate = LocalDate.now().minusDays(2);  // KamisApiClient 의 p_regday 와 일치
        if (retailPrice == null) {
            retailPrice = dto.getDpr2();
            reportedDate = reportedDate.minusDays(1);
        }
        if (retailPrice == null) {
            log.debug("[KAMIS-PROCESSOR] dpr1·dpr2 모두 비어 skip: name='{}'", dto.getProductName());
            return null;
        }

        // REQUIRES_NEW 로 저장 — chunk tx 와 분리해서 Writer skip 영향 없이 commit
        marketPriceSaveService.saveAll(matches, retailPrice, dto.getDpr4(), reportedDate);

        log.debug("[KAMIS-PROCESSOR] saved name='{}' price={} unit=kg reportedDate={} userCount={}",
                dto.getProductName(), retailPrice, reportedDate, matches.size());

        // Writer 는 skip (별도 tx 로 이미 저장)
        return null;
    }
}
