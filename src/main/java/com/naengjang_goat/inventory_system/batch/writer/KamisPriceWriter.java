package com.naengjang_goat.inventory_system.batch.writer;

import com.naengjang_goat.inventory_system.batch.dto.KamisPriceDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class KamisPriceWriter implements ItemWriter<KamisPriceDto> {

    @Override
    public void write(Chunk<? extends KamisPriceDto> chunk) {
        log.info("📦 Writer 수신 {}개", chunk.size());
        chunk.forEach(item ->
                log.info("➡ 저장 예정 데이터: {}", item)
        );
    }
}
