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

    // 통합 검색의 쇼핑 탭 사용: shopping.naver.com 직접은 봇 감지로 로그인 리다이렉트됨
    private static final String NAVER_BASE        = "https://search.naver.com/search.naver";
    private static final String SIKJAJAEWANG_BASE = "https://www.ewangmart.com/goods/search.do";

    /** 네이버 통합검색 쇼핑탭 — 업소용 키워드 자동 삽입. */
    public static String naverSearchUrl(String ingredientName) {
        return UriComponentsBuilder.fromUriString(NAVER_BASE)
                .queryParam("where", "shop")
                .queryParam("query", ingredientName + " 업소용")
                .build()
                .encode()
                .toUriString();
    }

    /** 식자재왕 검색 URL — 파라미터명이 'sword' (사이트 리뉴얼 후 keyword 는 무시됨). */
    public static String sikjajaewangSearchUrl(String ingredientName) {
        return UriComponentsBuilder.fromUriString(SIKJAJAEWANG_BASE)
                .queryParam("sword", ingredientName)
                .build()
                .encode()
                .toUriString();
    }
}
