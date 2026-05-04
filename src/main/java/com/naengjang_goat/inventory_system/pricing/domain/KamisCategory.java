package com.naengjang_goat.inventory_system.pricing.domain;

/**
 * KAMIS 품목 카테고리 — buySignal 임계값 결정용.
 *
 * threshold 출처: 2026-05-04 KAMIS yearlySalesList 6년 실측 intra-year CV
 *   분석 도구: KamisVolatilityAnalysisRunner.java + yearlySalesList API 직접 조회
 *
 *   조회 방법: yearlySalesList (action=yearlySalesList, p_yyyy=2024)
 *     → 각 연도별 cv_data (intra-year CV) 반환 → 2019~2024 평균 사용
 *
 *   VEGETABLES : 배추(16.1%)·양파(5.0%)·무(16.8%)·파(28.4%) → 4품목 avg = 16.6% → 0.17
 *                ※ 양파는 intra-year 변동 낮고 inter-year 변동 큼 (평년CV 26%)
 *                ※ 파는 계절성 강해 intra-year CV 높음
 *
 *   LIVESTOCK  : periodProductList API 미지원 (축산물 = 축산물품질평가원 EKAPE 별도 시스템)
 *                EKAPE 소비자가격 ekapepia.com (6년, 월별, aggregationUnit=MONTH) 실측:
 *                  소 등심 1등급(lt=4301,spec=22,grade=03): 4.3%
 *                  소 양지 1등급(lt=4301,spec=40,grade=03): 2.9%
 *                  돼지 삼겹살(lt=4304,spec=27):           8.3%
 *                  닭 육계kg(lt=9901,spec=99):             3.9%
 *                  4품목 소비자가격 단순평균 ≈ 4.9%
 *                → B2B 도매가는 소비자가격보다 변동 더 클 것으로 추정, 보수적 0.08 유지
 *                  (삼겹살 소비자가격 CV 8.3% 기준 거의 1:1, 전체 평균 4.9%의 약 1.6배)
 *
 *   SEAFOOD    : 고등어(7.7%)·명태(5.6%) → avg 6.7% → 0.07
 *                ※ 오징어 데이터 미확보, 2품목 기준
 *
 *   FRUITS     : 배(12.3%)·사과후지(12.9%) → avg 12.6% → 0.13
 *                ※ 배 2024년 이상치(42.8%) z-score 제거 후 계산
 *
 *   GRAINS     : 쌀(4.5%) → 0.05
 *                ※ 기존 0.06에서 실측치 반영 하향
 *
 *   PROCESSED  : 고정 3% (안정적 가공품, 별도 분석 불필요)
 *
 * ※ yearlySalesList 수정 확인 사항:
 *    - 무 정코드: 231 (기존 VolatilityRunner의 212는 양배추(양배추)임을 확인)
 *    - 사과 후지: item=411, kind=05 (기존 kind=01은 홍옥)
 *    - 대파(파): item=246, kind 생략 시 전체 파 데이터 반환
 *
 * buySignal 조건: currentPrice < monthAvg × (1 - buySignalThreshold)
 */
public enum KamisCategory {

    VEGETABLES(0.17),  // 채소류: 배추·양파·무·파 intra-year CV avg 16.6% (6년)
    LIVESTOCK (0.08),  // 축산물: EKAPE 소비자가격 4품목 avg 4.9% (삼겹살 8.3%), B2B 추정 보수적 유지
    SEAFOOD   (0.07),  // 수산물: 고등어·명태 intra-year CV avg 6.7% (6년)
    FRUITS    (0.13),  // 과일류: 배·사과후지 intra-year CV avg 12.6% (6년)
    GRAINS    (0.05),  // 곡물류: 쌀 intra-year CV 4.5% (6년)
    PROCESSED (0.03);  // 가공식품·조미료: 고정

    public final double buySignalThreshold;

    KamisCategory(double threshold) {
        this.buySignalThreshold = threshold;
    }
}
