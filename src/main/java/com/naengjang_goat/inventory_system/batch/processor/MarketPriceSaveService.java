package com.naengjang_goat.inventory_system.batch.processor;

import com.naengjang_goat.inventory_system.analysis.domain.MarketPrice;
import com.naengjang_goat.inventory_system.analysis.repository.MarketPriceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Writer의 chunk 트랜잭션에 참여한다. 같은 관측을 재실행하면 갱신한다. */
@Service
@RequiredArgsConstructor
public class MarketPriceSaveService {

    private final MarketPriceRepository marketPriceRepository;

    @Transactional(propagation = Propagation.MANDATORY)
    public void saveAll(List<MarketPrice> observations) {
        for (MarketPrice mp : observations) {
            marketPriceRepository.upsertKamisObservation(
                    mp.getIngredient().getId(), mp.getRetailPrice(), mp.getWholesalePrice(),
                    mp.getReportedDate(), mp.getPriceBasis(), mp.getObservationKey(),
                    mp.getKindName(), mp.getGrade());
        }
    }
}
