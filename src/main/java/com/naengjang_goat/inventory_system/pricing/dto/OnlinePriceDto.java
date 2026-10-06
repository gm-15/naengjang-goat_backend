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
    /**
     * Lombok 은 boolean isXxx 필드에 isXxx() getter 를 만들고 Jackson 은 이를 "xxx" 로 직렬화한다.
     * 프론트 계약(isDiscount / isLowest)을 지키기 위해 필드명은 discount / lowest 로 두고 JSON 이름을 고정.
     */
    @JsonProperty("isDiscount")
    private final boolean discount;
    private final Integer weightGrams;
    private final Long unitPricePerKg;
    @JsonProperty("isLowest")
    private final boolean lowest;
    private final LocalDateTime fetchedAt;
}
