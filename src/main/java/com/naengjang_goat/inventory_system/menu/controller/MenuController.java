package com.naengjang_goat.inventory_system.menu.controller;

import com.naengjang_goat.inventory_system.global.security.CustomUserDetails;
import com.naengjang_goat.inventory_system.menu.dto.MenuResponse;
import com.naengjang_goat.inventory_system.menu.repository.MenuRepository;
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

    private final MenuRepository menuRepository;

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
        List<MenuResponse> menus = menuRepository.findAllByUserIdWithBom(principal.getId())
                .stream()
                .map(MenuResponse::from)
                .toList();
        return ResponseEntity.ok(menus);
    }
}
