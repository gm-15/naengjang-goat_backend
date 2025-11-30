package com.naengjang_goat.inventory_system.batch.processor;

import com.naengjang_goat.inventory_system.analysis.domain.PriceHistory;
import com.naengjang_goat.inventory_system.batch.dto.KamisPriceDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
@Slf4j
public class KamisPriceProcessor implements ItemProcessor<KamisPriceDto, PriceHistory> {

    @Override
    public PriceHistory process(KamisPriceDto dto) {

        if (dto == null || dto.getProductName() == null) {
            return null;
        }

        PriceHistory entity = new PriceHistory();

        entity.setPriceDate(LocalDate.now());
        entity.setProductName(dto.getProductName());
        entity.setUnit(dto.getUnit());

        entity.setRetailPrice(normalize(dto.getDpr1()));
        entity.setWholesalePrice(normalize(dto.getDpr4()));

        return entity;
    }

    private String normalize(String v) {
        if (v == null || v.isBlank() || "-".equals(v)) return null;
        return v.replace(",", "").trim();
    }
}
