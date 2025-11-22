package com.naengjang_goat.inventory_system.batch.processor;

import com.naengjang_goat.inventory_system.batch.dto.KamisPriceDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class KamisPriceProcessor implements ItemProcessor<KamisPriceDto, KamisPriceDto> {

    @Override
    public KamisPriceDto process(KamisPriceDto item) {
        log.info("📌 시세 처리: {}", item);
        return item;
    }
}
