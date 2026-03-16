package com.naengjang_goat.inventory_system.menu.controller;

import com.naengjang_goat.inventory_system.menu.dto.MenuResponse;
import com.naengjang_goat.inventory_system.menu.repository.MenuRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 메뉴 조회 컨트롤러 (v2.1)
 * MockAuthFilter가 X-User-Id 헤더를 request attribute로 주입함.
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
     * Header:   X-User-Id: 1
     */
    @GetMapping
    public ResponseEntity<List<MenuResponse>> getMenus(
            @RequestAttribute("userId") Long userId
    ) {
        List<MenuResponse> menus = menuRepository.findAllByUserIdWithBom(userId)
                .stream()
                .map(MenuResponse::from)
                .toList();
        return ResponseEntity.ok(menus);
    }
}
