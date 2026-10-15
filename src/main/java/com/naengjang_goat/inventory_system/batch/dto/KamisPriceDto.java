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

    private String kindCode;
    private String kindName;
    private String rankCode;
    private String grade;
    private String priceBasis;
    private LocalDate requestedDate;
    private LocalDate observedDate;
    private String currentPrice;

    // sim, 2026-10-12 — KAMIS 응답 dpr1~dpr7
    //   p_convert_kg_yn=Y 환산 대상: dpr1(당일), dpr2(1일전), dpr3(1주일전), dpr4(2주일전)
    //   요청일 당일 가격이 "-" (아직 미게시) 인 경우 가 많아 fallback 용 dpr2 가 필요.
    // 기간별 가격이다. dpr4를 현재 도매가로 저장하지 않는다.
    private String dpr1;
    private String dpr2;
    private String dpr4;
}
