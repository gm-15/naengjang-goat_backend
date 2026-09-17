package com.naengjang_goat.inventory_system.pricing.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/** 상세 응답의 onlinePrices[] 한 행. */
@Getter
@Builder
@AllArgsConstructor
public class OnlinePriceDto {
    private final String source;          // "네이버_축산물", "식자재왕_축산/난류"
    private final String sourceLabel;     // "네이버", "식자재왕"
    private final String productName;
    private final String productUrl;
    private final String imageUrl;
    private final Integer price;
    private final String currency;
    // Lombok getter 가 isDiscount() 라 Jackson 은 "discount" 로 내보낸다. 프론트 키(isDiscount)에 맞춰 getter 에 고정 (park, 2026-09-17)
    @Getter(onMethod_ = @JsonProperty("isDiscount"))
    private final boolean isDiscount;
    private final Integer weightGrams;
    private final Long unitPricePerKg;
    @Getter(onMethod_ = @JsonProperty("isLowest"))
    private final boolean isLowest;
    private final LocalDateTime fetchedAt;
}
