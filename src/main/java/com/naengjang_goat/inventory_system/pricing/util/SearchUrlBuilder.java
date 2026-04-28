package com.naengjang_goat.inventory_system.pricing.util;

import org.springframework.web.util.UriComponentsBuilder;

/**
 * 외부 검색 URL 생성 유틸 (Tier 2 — 검색 URL 중계).
 *
 * 근거: plan_park_0423_01 §5, plan_park_0423_02 §2-1, plan_park_0426_01 §3-2
 *  - UC-CORE-2 리스트 응답의 externalLinks[]
 *  - BR2-11 fallback CTA (전 소스 파싱 실패 시 검색 URL 노출)
 *
 * 정책:
 *  - 네이버: "{재료명} 업소용" + sort=asc (저가순)
 *  - 식자재왕: 재료명 단독 (사이트 자체가 B2B 라 키워드 추가 불필요)
 *  - URL 인코딩은 UriComponentsBuilder.encode() 가 자동 처리 (한글 OK)
 */
public final class SearchUrlBuilder {

    private SearchUrlBuilder() {}

    private static final String NAVER_BASE        = "https://search.shopping.naver.com/search/all";
    private static final String SIKJAJAEWANG_BASE = "https://www.ewangmart.com/goods/search.do";

    /** 네이버 검색 URL — 업소용 키워드 자동 삽입 + 저가순. */
    public static String naverSearchUrl(String ingredientName) {
        return UriComponentsBuilder.fromUriString(NAVER_BASE)
                .queryParam("query", ingredientName + " 업소용")
                .queryParam("sort", "price_asc")
                .build()
                .encode()
                .toUriString();
    }

    /** 식자재왕 검색 URL — B2B 사이트라 키워드 단독. */
    public static String sikjajaewangSearchUrl(String ingredientName) {
        return UriComponentsBuilder.fromUriString(SIKJAJAEWANG_BASE)
                .queryParam("keyword", ingredientName)
                .build()
                .encode()
                .toUriString();
    }
}
