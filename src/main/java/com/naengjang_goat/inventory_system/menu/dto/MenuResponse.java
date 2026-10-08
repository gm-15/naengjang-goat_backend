package com.naengjang_goat.inventory_system.menu.dto;

import com.naengjang_goat.inventory_system.menu.domain.Menu;
import java.math.BigDecimal;
import java.util.List;

/**
 * GET /menus 응답 DTO (v2.1)
 * POS 화면 메뉴판 렌더링용.
 */
public record MenuResponse(
        Long menuId,
        String name,
        Integer price,
        List<RecipeItem> recipe
) {
    public record RecipeItem(
            Long ingredientId, String ingredientName, String baseUnit,
            BigDecimal requiredQuantity, String unit) {}

    public static MenuResponse from(Menu menu) {
        return new MenuResponse(menu.getId(), menu.getName(), menu.getPrice(),
                menu.getBom().stream().map(bom -> new RecipeItem(
                        bom.getIngredient().getId(), bom.getIngredient().getName(),
                        bom.getIngredient().getBaseUnit(), bom.getRequiredQuantity(), bom.getUnit()))
                        .toList());
    }
}
