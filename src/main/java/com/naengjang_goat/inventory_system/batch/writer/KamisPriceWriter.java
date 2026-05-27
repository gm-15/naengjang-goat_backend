package com.naengjang_goat.inventory_system.batch.writer;

import com.naengjang_goat.inventory_system.analysis.domain.MarketPrice;
import com.naengjang_goat.inventory_system.analysis.repository.MarketPriceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class KamisPriceWriter implements ItemWriter<MarketPrice> {

    private final MarketPriceRepository repository;

    @Override
    public void write(Chunk<? extends MarketPrice> chunk) {
        repository.saveAll(chunk.getItems());
        log.info("[KAMIS-WRITER] saved {} rows", chunk.size());
    }
}
