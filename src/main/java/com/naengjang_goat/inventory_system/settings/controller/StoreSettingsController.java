package com.naengjang_goat.inventory_system.settings.controller;

import com.naengjang_goat.inventory_system.global.security.CustomUserDetails;
import com.naengjang_goat.inventory_system.settings.dto.StoreSettingsRequest;
import com.naengjang_goat.inventory_system.settings.dto.StoreSettingsResponse;
import com.naengjang_goat.inventory_system.settings.service.StoreSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 점주 영업 설정 API.
 *
 * GET  /settings  → 현재 설정 조회 (미설정 시 configured=false)
 * PUT  /settings  → 설정 저장·수정 (upsert)
 *
 * 인증: JWT Bearer Token (Authorization 헤더)
 */
@RestController
@RequestMapping("/settings")
@RequiredArgsConstructor
public class StoreSettingsController {

    private final StoreSettingsService settingsService;

    @GetMapping
    public ResponseEntity<StoreSettingsResponse> getSettings(
            @AuthenticationPrincipal CustomUserDetails principal) {
        return ResponseEntity.ok(settingsService.getSettings(principal.getId()));
    }

    @PutMapping
    public ResponseEntity<StoreSettingsResponse> upsertSettings(
            @AuthenticationPrincipal CustomUserDetails principal,
            @RequestBody StoreSettingsRequest request) {
        return ResponseEntity.ok(settingsService.upsertSettings(principal.getId(), request));
    }
}
