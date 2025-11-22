package com.naengjang_goat.inventory_system.analysis.batch.writer;

import com.naengjang_goat.inventory_system.analysis.batch.dto.KamisPriceDto;
import com.naengjang_goat.inventory_system.analysis.domain.PriceHistory;
import com.naengjang_goat.inventory_system.analysis.repository.PriceHistoryRepository;
import com.naengjang_goat.inventory_system.inventory.domain.RawMaterial;
import com.naengjang_goat.inventory_system.inventory.repository.RawMaterialRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class KamisPriceWriter implements ItemWriter<KamisPriceDto> {

    private final PriceHistoryRepository priceHistoryRepository;
    private final RawMaterialRepository rawMaterialRepository;

    @Override
    public void write(Chunk<? extends KamisPriceDto> chunk) {

        chunk.getItems().forEach(dto -> {
            RawMaterial material =
                    rawMaterialRepository.findByName(dto.getProductName()).orElse(null);

            if (material == null) {
                log.warn("❌ 원재료 매칭 실패 → {}", dto.getProductName());
                return;
            }

            PriceHistory entity = new PriceHistory();
            entity.setRawMaterial(material);
            entity.setPriceDate(dto.getPriceDate());
            entity.setUnit(dto.getUnit());
            entity.setRetailPrice(dto.getRetailPrice());
            entity.setWholesalePrice(dto.getWholesalePrice());

            priceHistoryRepository.save(entity);
        });
    }
}
