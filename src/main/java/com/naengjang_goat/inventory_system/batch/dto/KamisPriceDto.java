package com.naengjang_goat.inventory_system.batch.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class KamisPriceDto {

    private String itemCode;
    private String productName;
    private String unit;

    private String dpr1; // 소매가
    private String dpr4; // 도매가
}
