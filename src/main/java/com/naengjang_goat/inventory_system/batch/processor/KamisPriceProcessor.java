package com.naengjang_goat.inventory_system.batch.processor;

import com.naengjang_goat.inventory_system.analysis.domain.MarketPrice;
import com.naengjang_goat.inventory_system.batch.dto.KamisPriceDto;
import com.naengjang_goat.inventory_system.inventory.repository.IngredientRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

/** 값 변환만 수행한다. 저장은 Writer의 chunk 트랜잭션에서 처리한다. */
@Component
@RequiredArgsConstructor
public class KamisPriceProcessor implements ItemProcessor<KamisPriceDto, List<MarketPrice>> {
    private final IngredientRepository ingredients;

    @Override
    public List<MarketPrice> process(KamisPriceDto dto) {
        if (dto == null || dto.getProductName() == null || dto.getCurrentPrice() == null
                || dto.getObservedDate() == null || dto.getRequestedDate() == null
                || dto.getObservedDate().isAfter(dto.getRequestedDate())
                || !("WHOLESALE".equals(dto.getPriceBasis()) || "RETAIL".equals(dto.getPriceBasis()))) {
            return null;
        }
        boolean hasCode = dto.getItemCode() != null && !dto.getItemCode().isBlank();
        var matches = new ArrayList<>(hasCode
                ? ingredients.findAllByKamisItemCode(dto.getItemCode())
                : List.<com.naengjang_goat.inventory_system.inventory.domain.Ingredient>of());
        // 한 점주의 코드 매칭 성공 때문에 다른 점주의 미등록 코드 재료를 누락하지 않는다.
        matches.addAll(ingredients.findAllByName(dto.getProductName()).stream()
                .filter(i -> !hasCode || i.getKamisItemCode() == null || i.getKamisItemCode().isBlank())
                .toList());
        if (matches.isEmpty()) return null;
        String key = observationKey(dto);
        return matches.stream().map(ingredient -> {
            var row = new MarketPrice();
            row.setIngredient(ingredient);
            row.setSource("KAMIS");
            row.setPriceBasis(dto.getPriceBasis());
            row.setRetailPrice("RETAIL".equals(dto.getPriceBasis()) ? dto.getCurrentPrice() : null);
            row.setWholesalePrice("WHOLESALE".equals(dto.getPriceBasis()) ? dto.getCurrentPrice() : null);
            row.setUnit("kg");
            row.setReportedDate(dto.getObservedDate());
            row.setObservationKey(key);
            row.setKindName(dto.getKindName());
            row.setGrade(dto.getGrade());
            return row;
        }).toList();
    }

    private String observationKey(KamisPriceDto dto) {
        String identity = String.join("\u001f", dto.getPriceBasis(), value(dto.getItemCode()),
                value(dto.getProductName()), value(dto.getKindCode()), value(dto.getKindName()),
                value(dto.getRankCode()), value(dto.getGrade()), "kg");
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(identity.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private String value(String value) { return value == null ? "" : value.trim(); }
}
