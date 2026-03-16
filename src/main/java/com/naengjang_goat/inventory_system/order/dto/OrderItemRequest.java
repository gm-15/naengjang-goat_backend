package com.naengjang_goat.inventory_system.order.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record OrderItemRequest(
        @NotNull Long menuId,
        @Min(1)  Integer quantity
) {}
