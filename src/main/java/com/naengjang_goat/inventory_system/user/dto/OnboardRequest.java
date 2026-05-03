package com.naengjang_goat.inventory_system.user.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * POST /api/users/onboard 요청 DTO.
 *
 * categories 허용값: KOREAN / WESTERN / CHINESE / JAPANESE / OTHER
 * 복수 선택 가능. 예: ["KOREAN", "WESTERN"]
 */
public record OnboardRequest(
        @NotEmpty List<String> categories
) {}
