package com.naengjang_goat.inventory_system.pricing.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 외부 검색 URL 1건.
 *  - 리스트 응답의 externalLinks[]
 *  - 상세 응답의 externalSearchLinks[] (BR2-11 fallback)
 */
@Getter
@Builder
@AllArgsConstructor
public class ExternalLinkDto {
    private final String source;  // "NAVER_SEARCH" | "SIKJAJAEWANG_SEARCH"
    private final String url;
}
