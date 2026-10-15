package com.naengjang_goat.inventory_system.batch.writer;

import com.naengjang_goat.inventory_system.analysis.domain.MarketPrice;
import com.naengjang_goat.inventory_system.batch.processor.MarketPriceSaveService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class KamisPriceWriter implements ItemWriter<List<MarketPrice>> {

    private final MarketPriceSaveService saver;

    @Override
    public void write(Chunk<? extends List<MarketPrice>> chunk) {
        var observations = chunk.getItems().stream().flatMap(List::stream).toList();
        saver.saveAll(observations);
        log.info("[KAMIS-WRITER] upserted {} observations from {} matched items", observations.size(), chunk.size());
    }
}
