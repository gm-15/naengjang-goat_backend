package com.naengjang_goat.inventory_system.batch.writer;

import com.naengjang_goat.inventory_system.analysis.domain.PriceHistory;
import com.naengjang_goat.inventory_system.analysis.repository.PriceHistoryRepository;
import com.naengjang_goat.inventory_system.inventory.repository.RawMaterialRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class KamisPriceWriter implements ItemWriter<PriceHistory> {

    private final PriceHistoryRepository priceHistoryRepository;
    private final RawMaterialRepository rawMaterialRepository;

    @Override
    public void write(Chunk<? extends PriceHistory> items) {
        items.forEach(ph ->
                rawMaterialRepository.findByName(ph.getProductName())
                        .ifPresent(ph::setRawMaterial)
        );
        priceHistoryRepository.saveAll(items);
    }
}
