package com.naengjang_goat.inventory_system.menu.controller;

import com.naengjang_goat.inventory_system.global.security.CustomUserDetails;
import com.naengjang_goat.inventory_system.menu.dto.MenuResponse;
import com.naengjang_goat.inventory_system.menu.dto.MenuRequest;
import com.naengjang_goat.inventory_system.menu.service.MenuService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 메뉴 조회 컨트롤러 (v2.2 — JWT 인증 복구)
 *
 * 인증: JWT Bearer Token
 */
@RestController
@RequestMapping("/menus")
@RequiredArgsConstructor
public class MenuController {

    private final MenuService menuService;

    /**
     * GET /menus
     * 점주의 전체 메뉴 목록 조회.
     *
     * Response: [ { "menuId": 1, "name": "제육볶음", "price": 9000 }, ... ]
     */
    @GetMapping
    public ResponseEntity<List<MenuResponse>> getMenus(
            @AuthenticationPrincipal CustomUserDetails principal
    ) {
        return ResponseEntity.ok(menuService.list(principal.getId()));
    }

    @PostMapping
    public ResponseEntity<MenuResponse> create(
            @AuthenticationPrincipal CustomUserDetails principal,
            @Valid @RequestBody MenuRequest request) {
        return ResponseEntity.ok(menuService.create(principal.getId(), request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<MenuResponse> update(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable Long id,
            @Valid @RequestBody MenuRequest request) {
        return ResponseEntity.ok(menuService.update(principal.getId(), id, request));
    }
}
