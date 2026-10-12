package com.naengjang_goat.inventory_system.batch.processor;

import com.naengjang_goat.inventory_system.analysis.domain.MarketPrice;
import com.naengjang_goat.inventory_system.analysis.repository.MarketPriceRepository;
import com.naengjang_goat.inventory_system.inventory.domain.Ingredient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * sim, 2026-10-12 — KAMIS 가격을 "모든 user Ingredient" 에 복제 저장.
 *
 * KamisPriceProcessor 가 Spring Batch chunk 트랜잭션 안에서 save 하면
 * Processor 가 null 을 반환해 Writer 가 skip 하는 흐름에서 chunk tx 가 rollback 되어
 * 저장이 유실되는 현상이 확인됨.
 *
 * 이 서비스는 REQUIRES_NEW 로 **별도 트랜잭션** 을 열어 저장만 commit 하고 돌아간다.
 * 이후 chunk tx 가 어떻게 처리되든 영향 없음.
 */
@Service
@RequiredArgsConstructor
public class MarketPriceSaveService {

    private final MarketPriceRepository marketPriceRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveAll(List<Ingredient> ingredients,
                        String retailPrice,
                        String wholesalePrice,
                        LocalDate reportedDate) {
        for (Ingredient ingredient : ingredients) {
            MarketPrice mp = new MarketPrice();
            mp.setIngredient(ingredient);
            mp.setRetailPrice(retailPrice);
            mp.setWholesalePrice(wholesalePrice);
            mp.setUnit("kg");
            mp.setReportedDate(reportedDate);
            marketPriceRepository.save(mp);
        }
    }
}
