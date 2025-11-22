package com.naengjang_goat.inventory_system.batch.processor;

import com.naengjang_goat.inventory_system.batch.dto.KamisPriceDto;
import com.naengjang_goat.inventory_system.analysis.domain.PriceHistory;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class KamisPriceProcessor implements ItemProcessor<KamisPriceDto, PriceHistory> {

    @Override
    public PriceHistory process(KamisPriceDto item) {
        PriceHistory entity = new PriceHistory();
        entity.setPriceDate(LocalDate.now());
        entity.setProductName(item.getProductName());
        entity.setUnit(item.getUnit());
        entity.setRetailPrice(item.getDpr1());
        entity.setWholesalePrice(item.getDpr4());
        return entity;
    }
}
