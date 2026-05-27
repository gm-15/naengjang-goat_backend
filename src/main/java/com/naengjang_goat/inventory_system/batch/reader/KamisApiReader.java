package com.naengjang_goat.inventory_system.batch.reader;

import com.naengjang_goat.inventory_system.batch.dto.KamisPriceDto;
import com.naengjang_goat.inventory_system.batch.service.KamisApiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemReader;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;

@Component
@StepScope
@RequiredArgsConstructor
@Slf4j
public class KamisApiReader implements ItemReader<KamisPriceDto> {

    private final KamisApiClient kamisApiClient;
    private Iterator<KamisPriceDto> iterator;

    @Override
    public KamisPriceDto read() {
        if (iterator == null) {
            List<KamisPriceDto> list = kamisApiClient.fetchAllCategories();
            iterator = list != null ? list.iterator() : Collections.emptyIterator();
            log.info("[KAMIS-READER] Loaded {} items (6 categories)",
                    list != null ? list.size() : 0);
        }
        return iterator.hasNext() ? iterator.next() : null;
    }
}
