package com.naengjang_goat.inventory_system.inventory.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class OrderRequestDto {
    private Long recipeId;
    private int quantity;
}
