package com.naengjang_goat.inventory_system.batch.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class KamisPriceDto {

    private String itemCode;
    private String productName;
    private String unit;

    // sim, 2026-10-12 — KAMIS 응답 dpr1~dpr7
    //   p_convert_kg_yn=Y 환산 대상: dpr1(당일), dpr2(1일전), dpr3(1주일전), dpr4(2주일전)
    //   요청일 당일 가격이 "-" (아직 미게시) 인 경우 가 많아 fallback 용 dpr2 가 필요.
    //   reportedDate 보정은 Processor 가 처리.
    private String dpr1;
    private String dpr2;
    private String dpr4;
}
