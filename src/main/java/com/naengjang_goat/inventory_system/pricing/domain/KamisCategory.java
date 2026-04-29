package com.naengjang_goat.inventory_system.pricing.domain;

/**
 * KAMIS 품목 카테고리 — buySignal 임계값 결정용.
 *
 * threshold 출처: 2026-04-28 KAMIS periodProductList 실측 CV (최근 13개월)
 *   분석 도구: KamisVolatilityAnalysisRunner.java
 *
 *   VEGETABLES : 배추(0.210, 제거)·양파(0.149)·마늘(0.059, 제거)·대파(0.161)·무(0.168)·건고추(0.156)
 *                → z-score 이상치 제거 후 4품목 avg = 0.1583  → 0.16
 *   LIVESTOCK  : periodProductList API 미지원 (축산물 = 축산물품질평가원 EKAPE 별도 시스템)
 *                → 업계 참고값 기반 고정 0.08
 *   SEAFOOD    : 고등어(0.137)·명태(0.020)·오징어(0.014) → avg 0.0569  → 0.06
 *   FRUITS     : 사과(0.051)·배(0.175) → avg 0.1134  → 0.11
 *   GRAINS     : 쌀(0.060) → 0.06
 *   PROCESSED  : 고정 3%
 *
 * ※ periodProductList는 최근 ~13개월 데이터만 반환 (날짜 파라미터 무시 확인됨).
 *    5년 이상 이력은 KAMIS 내부 별도 시스템 — 향후 재분석 시 갱신 필요.
 *
 * buySignal 조건: currentPrice < monthAvg × (1 - buySignalThreshold)
 */
public enum KamisCategory {

    VEGETABLES(0.16),  // 채소류: 양파·대파·무·건고추 CV avg 0.1583
    LIVESTOCK (0.08),  // 축산물: API 미지원, 업계 참고값 고정
    SEAFOOD   (0.06),  // 수산물: 고등어·명태·오징어 CV avg 0.0569
    FRUITS    (0.11),  // 과일류: 사과·배 CV avg 0.1134
    GRAINS    (0.06),  // 곡물류: 쌀 CV 0.060
    PROCESSED (0.03);  // 가공식품·조미료: 고정

    public final double buySignalThreshold;

    KamisCategory(double threshold) {
        this.buySignalThreshold = threshold;
    }
}
