package com.naengjang_goat.inventory_system.menu.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;

/** 메뉴 한 개 판매 시 사용하는 실제 재료 소모량. */
public record MenuRequest(
    @NotBlank @Size(max = 100) String name,
    @NotNull @PositiveOrZero Integer price,
    @NotEmpty @Size(max = 100) List<@NotNull @Valid RecipeItem> recipe) {
  public record RecipeItem(
      @NotNull @Positive Long ingredientId,
      @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 7, fraction = 3)
          BigDecimal requiredQuantity,
      @NotBlank @Pattern(regexp = "g|kg|ml|[lL]|개") String unit) {}
}
