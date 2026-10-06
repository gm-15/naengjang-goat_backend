package com.naengjang_goat.inventory_system.inventory.service;

import static org.assertj.core.api.Assertions.*;

import com.naengjang_goat.inventory_system.global.util.UnitConverter;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** Stock now uses InventoryBatch; guard the unit boundary used by upload/receipt. */
class InventoryServiceTest {
  private final UnitConverter units = new UnitConverter();

  @Test
  void scalesCompatibleUnitsWithoutLosingPrecision() {
    assertThat(units.convert("kg", new BigDecimal("0.5"), "g")).isEqualByComparingTo("500");
    assertThat(units.convert("g", new BigDecimal("500"), "kg")).isEqualByComparingTo("0.5");
    assertThat(units.convert("L", new BigDecimal("0.75"), "ml")).isEqualByComparingTo("750");
  }

  @Test
  void rejectsWeightVolumeAndCountMixing() {
    assertThatThrownBy(() -> units.convert("g", BigDecimal.ONE, "ml"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> units.convert("개", BigDecimal.ONE, "g"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> units.convert("포장", BigDecimal.ONE, "포장"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
