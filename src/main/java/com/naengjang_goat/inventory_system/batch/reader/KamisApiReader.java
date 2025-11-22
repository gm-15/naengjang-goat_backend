package com.naengjang_goat.inventory_system.batch.reader;

import com.naengjang_goat.inventory_system.batch.dto.KamisPriceDto;
import com.naengjang_goat.inventory_system.batch.service.KamisApiClient;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.item.ItemReader;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class KamisApiReader implements ItemReader<KamisPriceDto> {

    private final KamisApiClient apiClient;

    private List<KamisPriceDto> cache;
    private int index = 0;

    @Override
    public KamisPriceDto read() {
        if (cache == null) {
            cache = apiClient.fetchPrices();
        }
        return index < cache.size() ? cache.get(index++) : null;
    }
}
