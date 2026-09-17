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

    /** 등급 (상품·중품·1등급·大 등). 품목당 대표 행을 고를 때 사용. */
    private String rank;

    /**
     * 조회일(p_regday) 당일 가격.
     * p_product_cls_code=02 로 호출하므로 도매가이고, kg 단위 품목은 p_convert_kg_yn=Y 로 원/kg 환산값.
     * dpr2~dpr7 은 1일전·1주일전·2주일전·1개월전·1년전·평년 가격이라 쓰지 않는다.
     * park, 2026-09-17 — 기존에 dpr4 를 '도매가' 로 주석해 저장했으나 실제로는 2주일 전 가격이었음.
     */
    private String dpr1;

    /**
     * 시세 공표일. 30일 backfill 시 각 응답별로 달라짐.
     * 없으면 Processor 가 어제(LocalDate.now().minusDays(1)) 로 fallback.
     * sim, 2026-06-05 — kim 인수인계서 5-3 섹션.
     */
    private LocalDate reportedDate;
}
