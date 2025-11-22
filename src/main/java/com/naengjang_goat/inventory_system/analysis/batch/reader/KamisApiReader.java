package com.naengjang_goat.inventory_system.analysis.batch.reader;

import com.naengjang_goat.inventory_system.analysis.batch.dto.KamisPriceDto;
import com.naengjang_goat.inventory_system.analysis.batch.service.KamisApiClient;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.item.ItemReader;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.List;

@Component
@RequiredArgsConstructor
public class KamisApiReader implements ItemReader<KamisPriceDto> {

    private final KamisApiClient apiClient;

    private Iterator<KamisPriceDto> iterator;

    @Override
    public KamisPriceDto read() {
        if (iterator == null) {
            List<KamisPriceDto> prices = apiClient.fetchDailySales();
            iterator = prices.iterator();
        }

        return iterator.hasNext() ? iterator.next() : null;
    }
}
