package com.naengjang_goat.inventory_system.batch.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class KamisPriceDto {

    private String itemCode;
    private String productName;
    private String unit;

    private String dpr1; // 소매가
    private String dpr4; // 도매가

    /**
     * 시세 공표일. 30일 backfill 시 각 응답별로 달라짐.
     * 없으면 Processor 가 어제(LocalDate.now().minusDays(1)) 로 fallback.
     * sim, 2026-06-05 — kim 인수인계서 5-3 섹션.
     */
    private LocalDate reportedDate;
}
