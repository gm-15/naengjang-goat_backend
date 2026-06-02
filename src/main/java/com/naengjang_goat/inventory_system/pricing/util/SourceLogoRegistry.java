package com.naengjang_goat.inventory_system.pricing.util;

import java.util.Map;

/**
 * 온라인 채널명 → 로고 URL 정적 매핑.
 *
 * 시안 {@code /lowest-price/{id}} 카드의 좌측 로고용.
 * sim 영역 — 데이터 출처별 시각 자산 통합 관리.
 *
 * 주의:
 *  - 현재 로고 URL은 placeholder. 실제 자산 배포 후 교체.
 *  - 신규 채널(쿠팡 등) 추가 시 본 클래스만 수정.
 *  - {@link com.naengjang_goat.inventory_system.pricing.dto.OnlinePriceDto#sourceLabel}
 *    값을 키로 사용.
 *
 * @author sim
 * @since 2026-06-01
 */
public final class SourceLogoRegistry {

    private SourceLogoRegistry() {
        throw new UnsupportedOperationException("SourceLogoRegistry is a static utility");
    }

    /**
     * sourceLabel → 로고 URL.
     * 키 매칭은 정규화 후 비교 (공백 trim).
     */
    private static final Map<String, String> LOGO_BY_LABEL = Map.of(
            "쿠팡",     "https://static.naengjang-goat.com/logos/coupang.png",
            "네이버",   "https://static.naengjang-goat.com/logos/naver.png",
            "마켓컬리", "https://static.naengjang-goat.com/logos/kurly.png",
            "식자재왕", "https://static.naengjang-goat.com/logos/ewangmart.png"
    );

    /** 기본 로고 (매칭 실패 시 fallback) */
    private static final String DEFAULT_LOGO = "https://static.naengjang-goat.com/logos/default.png";

    /**
     * 채널 라벨에 해당하는 로고 URL 반환.
     *
     * @param label OnlinePriceDto.sourceLabel (예: "쿠팡", "네이버")
     * @return 로고 URL. 매칭 실패 시 DEFAULT_LOGO.
     */
    public static String resolve(String label) {
        if (label == null || label.isBlank()) {
            return DEFAULT_LOGO;
        }
        return LOGO_BY_LABEL.getOrDefault(label.trim(), DEFAULT_LOGO);
    }
}
