package com.naengjang_goat.inventory_system.batch.dto;

import lombok.Data;

@Data
public class KamisPriceDto {

    private String productName;   // 품목 이름
    private String unit;          // 단위 (e.g., kg, g)
    private String dpr1;          // 소매가
    private String dpr2;          // 전일가
    private String dpr3;          // 등락율
    private String dpr4;          // 도매가
}
