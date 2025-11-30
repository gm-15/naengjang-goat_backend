package com.naengjang_goat.inventory_system.batch.processor;

import com.naengjang_goat.inventory_system.analysis.domain.PriceHistory;
import com.naengjang_goat.inventory_system.batch.dto.KamisPriceDto;
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
        // DB 컬럼: retail_price / wholesale_price
        entity.setRetailPrice(item.getDpr1());
        entity.setWholesalePrice(item.getDpr4());
        // raw_material_id, price, price_unit 은 아직 매핑 안함 → NULL 허용 전제

        return entity;
    }
}
