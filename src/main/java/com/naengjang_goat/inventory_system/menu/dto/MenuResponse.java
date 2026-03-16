package com.naengjang_goat.inventory_system.menu.dto;

import com.naengjang_goat.inventory_system.menu.domain.Menu;

/**
 * GET /menus 응답 DTO (v2.1)
 * POS 화면 메뉴판 렌더링용.
 */
public record MenuResponse(
        Long menuId,
        String name,
        Integer price
) {
    public static MenuResponse from(Menu menu) {
        return new MenuResponse(menu.getId(), menu.getName(), menu.getPrice());
    }
}
