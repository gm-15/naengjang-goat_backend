package com.naengjang_goat.inventory_system.batch.reader;

import com.naengjang_goat.inventory_system.batch.dto.KamisPriceDto;
import com.naengjang_goat.inventory_system.batch.service.KamisApiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.ItemReader;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class KamisApiReader implements ItemReader<KamisPriceDto> {

    private final KamisApiClient kamisApiClient;

    private Iterator<KamisPriceDto> iterator;

    @Override
    public KamisPriceDto read() {
        if (iterator == null) {
            List<KamisPriceDto> list = kamisApiClient.fetchDailySales();
            log.info("[KAMIS-READER] loaded {} items from api", list.size());
            iterator = list.iterator();
        }

        if (iterator.hasNext()) {
            return iterator.next();
        } else {
            return null; // end of stream
        }
    }
}
