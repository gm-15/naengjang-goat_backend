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

    private final KamisApiClient kamisApiClient;

    private List<KamisPriceDto> buffer;
    private int index = 0;

    @Override
    public KamisPriceDto read() {
        if (buffer == null) {
            buffer = kamisApiClient.fetchDailySales();
        }
        if (index >= buffer.size()) {
            return null;
        }
        return buffer.get(index++);
    }
}
