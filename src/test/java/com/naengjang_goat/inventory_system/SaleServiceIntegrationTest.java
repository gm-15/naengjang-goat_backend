package com.naengjang_goat.inventory_system;

import static org.assertj.core.api.Assertions.*;

import com.naengjang_goat.inventory_system.global.lock.*;
import com.naengjang_goat.inventory_system.inventory.domain.*;
import com.naengjang_goat.inventory_system.inventory.repository.*;
import com.naengjang_goat.inventory_system.menu.domain.*;
import com.naengjang_goat.inventory_system.menu.repository.MenuRepository;
import com.naengjang_goat.inventory_system.order.domain.ChannelType;
import com.naengjang_goat.inventory_system.order.dto.*;
import com.naengjang_goat.inventory_system.order.service.OrderService;
import com.naengjang_goat.inventory_system.user.domain.*;
import com.naengjang_goat.inventory_system.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** The retired Recipe/Inventory services were replaced by Menu/InventoryBatch. */
@SpringBootTest
@ActiveProfiles("test")
class SaleServiceIntegrationTest {
  @Autowired OrderService orders;
  @Autowired LockStrategyHolder locks;
  @Autowired UserRepository users;
  @Autowired IngredientRepository ingredients;
  @Autowired InventoryBatchRepository batches;
  @Autowired MenuRepository menus;
  User user;
  Ingredient tomato;
  Ingredient sauce;
  Menu menu;

  @BeforeEach
  void setup() {
    user = users.save(new User("sale_" + UUID.randomUUID(), "test", "테스트", Role.OWNER));
    tomato = ingredients.save(new Ingredient(user, "토마토", "g", BigDecimal.TEN));
    sauce = ingredients.save(new Ingredient(user, "소스", "g", BigDecimal.TEN));
    batches.save(
        new InventoryBatch(
            tomato,
            new BigDecimal("2000"),
            BigDecimal.ONE,
            LocalDate.now(),
            LocalDate.now().plusDays(7)));
    batches.save(
        new InventoryBatch(
            sauce,
            new BigDecimal("3000"),
            BigDecimal.ONE,
            LocalDate.now(),
            LocalDate.now().plusDays(7)));
    menu = new Menu(user, "토마토파스타", 9000);
    menu.addBom(new RecipeBom(menu, tomato, new BigDecimal("100"), "g"));
    menu.addBom(new RecipeBom(menu, sauce, new BigDecimal("150"), "g"));
    menu = menus.save(menu);
    locks.setCurrentType(LockType.PESSIMISTIC);
  }

  @AfterEach
  void restore() {
    locks.setCurrentType(LockType.REDISSON);
  }

  private OrderRequest order(int quantity) {
    return new OrderRequest(ChannelType.POS, List.of(new OrderItemRequest(menu.getId(), quantity)));
  }

  @Test
  void tenConcurrentSalesDeductExactly() throws Exception {
    try (var pool = Executors.newFixedThreadPool(10)) {
      var tasks = new ArrayList<Callable<Boolean>>();
      for (int i = 0; i < 10; i++)
        tasks.add(
            () -> {
              orders.processOrder(user.getId(), order(1));
              return true;
            });
      for (var future : pool.invokeAll(tasks))
        assertThat(future.get(30, TimeUnit.SECONDS)).isTrue();
    }
    assertThat(batches.sumQuantityByIngredientId(tomato.getId())).isEqualByComparingTo("1000");
    assertThat(batches.sumQuantityByIngredientId(sauce.getId())).isEqualByComparingTo("1500");
  }

  @Test
  void shortageInLaterIngredientRollsBackEarlierDeduction() {
    var small =
        batches
            .findAllByIngredientIdAndQuantityGreaterThanOrderByExpirationDateAsc(
                sauce.getId(), BigDecimal.ZERO)
            .getFirst();
    small.setQuantity(new BigDecimal("50"));
    batches.save(small);
    assertThatThrownBy(() -> orders.processOrder(user.getId(), order(1)))
        .isInstanceOf(Exception.class);
    assertThat(batches.sumQuantityByIngredientId(tomato.getId())).isEqualByComparingTo("2000");
    assertThat(batches.sumQuantityByIngredientId(sauce.getId())).isEqualByComparingTo("50");
  }
}
