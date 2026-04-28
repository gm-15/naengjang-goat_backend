package com.naengjang_goat.inventory_system.pricing.util;

import java.util.Map;

/**
 * 액체류 밀도 상수 (g/mL).
 *
 * 근거: plan_park_0426_01 §3-3
 *  - 1L = 1000g 가정 시 액체류에서 가격 왜곡 발생 (간장 +20%, 꿀 +40% 등)
 *  - Demo 부터 6종 핵심 재료 하드코딩으로 시연 보호
 *  - V1 에서 DB 테이블 (liquid_density) + 관리자 UI 로 마이그레이션
 *
 * 매칭 로직: 상품명에 키워드 포함 시 해당 밀도 사용. 없으면 1.0 (물 기준).
 */
public final class LiquidDensity {

    private LiquidDensity() {}

    /** 업소 핵심 액체 6종 + 알파. 추가 시 이 Map 만 수정. */
    private static final Map<String, Double> DENSITY = Map.ofEntries(
            Map.entry("간장",    1.20),
            Map.entry("액젓",    1.25),
            Map.entry("식용유",  0.92),
            Map.entry("참기름",  0.92),
            Map.entry("우유",    1.03),
            Map.entry("꿀",      1.40)
    );

    /**
     * 상품명에서 밀도 추출. 매칭 안 되면 1.0 (물).
     *
     * @param productName 상품명 (예: "양조간장 1.8L")
     * @return 밀도 (g/mL)
     */
    public static double resolve(String productName) {
        if (productName == null) {
            return 1.0;
        }
        return DENSITY.entrySet().stream()
                .filter(e -> productName.contains(e.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(1.0);
    }
}
