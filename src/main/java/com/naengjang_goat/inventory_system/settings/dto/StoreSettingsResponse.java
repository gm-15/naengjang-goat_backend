package com.naengjang_goat.inventory_system.settings.dto;

import com.naengjang_goat.inventory_system.settings.domain.DayOfWeekType;
import com.naengjang_goat.inventory_system.settings.domain.StoreSettings;

import java.time.LocalTime;

/**
 * GET /settings 응답.
 *
 * 예시:
 * {
 *   "openTime":     "11:00",
 *   "closeTime":    "22:00",
 *   "orderDay":     "MON",
 *   "inventoryDay": "SUN",
 *   "configured":   true
 * }
 *
 * configured = false 이면 설정 미완료 — 프론트에서 최초 설정 유도 화면 표시 가능.
 */
public record StoreSettingsResponse(
        LocalTime openTime,
        LocalTime closeTime,
        DayOfWeekType orderDay,
        DayOfWeekType inventoryDay,
        boolean configured
) {
    /** 설정이 존재할 때 */
    public static StoreSettingsResponse from(StoreSettings s) {
        return new StoreSettingsResponse(
                s.getOpenTime(),
                s.getCloseTime(),
                s.getOrderDay(),
                s.getInventoryDay(),
                true
        );
    }

    /** 설정이 없을 때 (미설정 상태) */
    public static StoreSettingsResponse empty() {
        return new StoreSettingsResponse(null, null, null, null, false);
    }
}
