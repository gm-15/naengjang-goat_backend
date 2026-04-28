package com.naengjang_goat.inventory_system.pricing.util;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 상품명에서 무게(g)를 파싱하는 공용 유틸.
 *
 * 정책 (plan_park_0423_01 §4, plan_park_0426_01 §3):
 *  - 단일 매칭만 성공 처리. 다중 매칭("500g 300g")은 혼란 방지로 null 반환
 *  - 박스/세트/혼합포장 키워드 포함 시 null 반환 (혼합 무게 차단)
 *  - L/mL 단위는 LiquidDensity 적용 후 g 으로 환산
 *
 * 실패 시 null → DB 의 unit_price_per_kg 도 자동으로 NULL (GENERATED COLUMN).
 * BR2-9: weight_grams = NULL row 는 onlinePrices 응답에서 제외.
 */
public final class WeightParser {

    private WeightParser() {}

    /** 숫자(소수점) + 단위 매칭. 단위는 g/kg/mg/ml/l 대소문자 허용. */
    private static final Pattern WEIGHT_PATTERN =
            Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(kg|g|mg|ml|l)\\b",
                    Pattern.CASE_INSENSITIVE);

    /** 발견 시 즉시 null 반환 (혼합·세트·박스 패키지). */
    private static final List<String> EXCLUDE_TOKENS = List.of(
            "박스", "세트", "혼합", "선물", "+", "x", "X"
    );

    /**
     * 상품명에서 무게(g) 파싱. 실패 시 null.
     *
     * @param productName 상품명 (예: "한돈 닭고기 1kg", "간장 1.8L")
     * @return 무게(g 단위 정수) 또는 null
     */
    public static Integer parseGrams(String productName) {
        if (productName == null || productName.isBlank()) {
            return null;
        }

        // 1. 혼합/세트 키워드 → 즉시 null
        for (String token : EXCLUDE_TOKENS) {
            if (productName.contains(token)) {
                return null;
            }
        }

        // 2. 모든 무게 패턴 수집
        Matcher matcher = WEIGHT_PATTERN.matcher(productName);
        Double firstValue = null;
        String firstUnit = null;
        int matchCount = 0;

        while (matcher.find()) {
            matchCount++;
            if (matchCount > 1) {
                // 다중 매칭 — 혼란 방지 null
                return null;
            }
            try {
                firstValue = Double.parseDouble(matcher.group(1));
                firstUnit = matcher.group(2).toLowerCase();
            } catch (NumberFormatException e) {
                return null;
            }
        }

        if (firstValue == null) {
            return null;
        }

        // 3. 단위별 g 환산
        double grams = switch (firstUnit) {
            case "kg" -> firstValue * 1000.0;
            case "g"  -> firstValue;
            case "mg" -> firstValue / 1000.0;
            case "l"  -> firstValue * 1000.0 * LiquidDensity.resolve(productName);
            case "ml" -> firstValue * LiquidDensity.resolve(productName);
            default   -> 0;
        };

        if (grams <= 0) {
            return null;
        }

        return (int) Math.round(grams);
    }
}
