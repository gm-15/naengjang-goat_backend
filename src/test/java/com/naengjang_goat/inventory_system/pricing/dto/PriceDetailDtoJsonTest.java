package com.naengjang_goat.inventory_system.pricing.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GET /prices/{ingredientId} 응답의 JSON 키가 프론트 타입과 같은지 확인.
 * 기준: smu_capstone_project frontend/src/app/types/ingredient.ts 의 PriceDetail · KamisPrice · OnlinePrice
 */
class PriceDetailDtoJsonTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    @DisplayName("PriceDetail · KamisPrice 키가 프론트 타입과 같다")
    void priceDetailKeys() throws Exception {
        PriceDetailDto dto = PriceDetailDto.builder()
                .ingredientId(1L).name("배추").unit("g")
                .kamis(KamisPriceDto.builder()
                        .currentPricePerKg(1923L).priceDate(LocalDate.of(2026, 9, 16)).weekAvg(1800L).monthAvg(1700L)
                        .build())
                .onlinePrices(List.of())
                .externalSearchLinks(List.of(ExternalLinkDto.builder().source("NAVER_SEARCH").url("https://x").build()))
                .build();

        JsonNode json = objectMapper.valueToTree(dto);

        assertThat(fieldNames(json))
                .containsExactlyInAnyOrder("ingredientId", "name", "unit", "kamis", "onlinePrices", "externalSearchLinks");
        assertThat(fieldNames(json.get("kamis")))
                .containsExactlyInAnyOrder("currentPricePerKg", "priceDate", "weekAvg", "monthAvg");
        assertThat(fieldNames(json.get("externalSearchLinks").get(0)))
                .containsExactlyInAnyOrder("source", "url");
    }

    @Test
    @DisplayName("OnlinePrice 의 boolean 키는 isDiscount · isLowest 로 나간다")
    void onlinePriceKeys() throws Exception {
        OnlinePriceDto dto = OnlinePriceDto.builder()
                .source("네이버_채소").sourceLabel("네이버").productName("배추 3포기").productUrl("https://x")
                .imageUrl(null).price(9900).currency("KRW").isDiscount(true)
                .weightGrams(6000).unitPricePerKg(1650L).isLowest(true).fetchedAt(LocalDateTime.of(2026, 9, 17, 3, 0))
                .build();

        JsonNode json = objectMapper.valueToTree(dto);

        assertThat(fieldNames(json)).containsExactlyInAnyOrder(
                "source", "sourceLabel", "productName", "productUrl", "imageUrl", "price", "currency",
                "isDiscount", "weightGrams", "unitPricePerKg", "isLowest", "fetchedAt");
        assertThat(json.get("isDiscount").asBoolean()).isTrue();
        assertThat(json.get("isLowest").asBoolean()).isTrue();
    }

    private static List<String> fieldNames(JsonNode node) {
        return iteratorToList(node.fieldNames());
    }

    private static List<String> iteratorToList(java.util.Iterator<String> it) {
        List<String> names = new java.util.ArrayList<>();
        it.forEachRemaining(names::add);
        return names;
    }
}
