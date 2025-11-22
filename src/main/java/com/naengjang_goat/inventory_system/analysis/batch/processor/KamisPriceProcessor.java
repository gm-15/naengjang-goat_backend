package com.naengjang_goat.inventory_system.analysis.batch.processor;

import com.naengjang_goat.inventory_system.analysis.batch.dto.KamisPriceDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Slf4j
@Component
public class KamisPriceProcessor implements ItemProcessor<KamisPriceDto, KamisPriceDto> {

    @Override
    public KamisPriceDto process(KamisPriceDto item) {

        item.setPriceDate(LocalDate.now()); // 오늘 날짜 저장

        return item;
    }
}
