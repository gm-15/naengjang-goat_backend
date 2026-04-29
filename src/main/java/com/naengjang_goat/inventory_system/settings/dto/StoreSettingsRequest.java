package com.naengjang_goat.inventory_system.settings.dto;

import com.naengjang_goat.inventory_system.settings.domain.DayOfWeekType;

import java.time.LocalTime;

/**
 * PUT /settings 요청 바디.
 *
 * 예시:
 * {
 *   "openTime":     "11:00",
 *   "closeTime":    "22:00",
 *   "orderDay":     "MON",
 *   "inventoryDay": "SUN"
 * }
 */
public record StoreSettingsRequest(
        LocalTime openTime,
        LocalTime closeTime,
        DayOfWeekType orderDay,
        DayOfWeekType inventoryDay
) {}
