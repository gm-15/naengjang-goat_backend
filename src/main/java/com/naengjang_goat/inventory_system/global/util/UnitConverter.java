package com.naengjang_goat.inventory_system.global.util;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 단위 변환기 (v2.1)
 * 재고 차감 시 단위 불일치 문제 해결
 *
 * 문제 예시:
 *   RecipeBom:     마늘 50 g 필요
 *   InventoryBatch: 마늘 0.5 kg 보유
 *   → 단위가 다르면 차감 불가 → 기준 단위(g)로 정규화 후 연산
 *
 * 지원 변환:
 *   WEIGHT: g ↔ kg  (× or ÷ 1000)
 *   VOLUME: ml ↔ L  (× or ÷ 1000)
 *   COUNT:  개      (변환 없음)
 *
 * 반올림 정책: HALF_UP (부동소수점 누적 오차 방지)
 * 모든 연산: BigDecimal
 */
@Component
public class UnitConverter {

    private static final BigDecimal THOUSAND = new BigDecimal("1000");
    private static final int SCALE = 3;

    /**
     * 임의 단위 → 기준 단위(g, ml, 개)로 정규화
     * 예: 0.5 kg → 500 g
     */
    public BigDecimal toBase(String unit, BigDecimal value) {
        return switch (unit.toLowerCase()) {
            case "kg" -> value.multiply(THOUSAND).setScale(SCALE, RoundingMode.HALF_UP);
            case "l"  -> value.multiply(THOUSAND).setScale(SCALE, RoundingMode.HALF_UP);
            case "g", "ml", "개" -> value.setScale(SCALE, RoundingMode.HALF_UP);
            default -> throw new IllegalArgumentException("지원하지 않는 단위입니다: " + unit);
        };
    }

    /**
     * 기준 단위 → 임의 단위로 역변환
     * 예: 500 g → 0.5 kg
     */
    public BigDecimal fromBase(String targetUnit, BigDecimal baseValue) {
        return switch (targetUnit.toLowerCase()) {
            case "kg" -> baseValue.divide(THOUSAND, SCALE, RoundingMode.HALF_UP);
            case "l"  -> baseValue.divide(THOUSAND, SCALE, RoundingMode.HALF_UP);
            case "g", "ml", "개" -> baseValue.setScale(SCALE, RoundingMode.HALF_UP);
            default -> throw new IllegalArgumentException("지원하지 않는 단위입니다: " + targetUnit);
        };
    }

    /**
     * 두 단위 간 직접 변환 (source → target)
     * 예: 0.5 kg → g = 500 g
     */
    public BigDecimal convert(String sourceUnit, BigDecimal value, String targetUnit) {
        if (sourceUnit.equalsIgnoreCase(targetUnit)) {
            return value.setScale(SCALE, RoundingMode.HALF_UP);
        }
        BigDecimal baseValue = toBase(sourceUnit, value);
        return fromBase(targetUnit, baseValue);
    }

    /**
     * 단위 그룹(WEIGHT/VOLUME/COUNT) 호환 여부 확인
     * 호환되지 않으면 재고 차감 전 IllegalArgumentException
     */
    public void validateCompatible(String unit1, String unit2) {
        String group1 = getUnitGroup(unit1);
        String group2 = getUnitGroup(unit2);
        if (!group1.equals(group2)) {
            throw new IllegalArgumentException(
                    "단위 변환 불가: " + unit1 + " → " + unit2 + " (단위 그룹 불일치)");
        }
    }

    private String getUnitGroup(String unit) {
        return switch (unit.toLowerCase()) {
            case "g", "kg" -> "WEIGHT";
            case "ml", "l" -> "VOLUME";
            case "개"       -> "COUNT";
            default -> throw new IllegalArgumentException("지원하지 않는 단위입니다: " + unit);
        };
    }
}
