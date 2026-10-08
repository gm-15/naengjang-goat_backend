package com.naengjang_goat.inventory_system.menu.service;

import com.naengjang_goat.inventory_system.global.util.UnitConverter;
import com.naengjang_goat.inventory_system.inventory.domain.Ingredient;
import com.naengjang_goat.inventory_system.inventory.repository.IngredientRepository;
import com.naengjang_goat.inventory_system.menu.domain.Menu;
import com.naengjang_goat.inventory_system.menu.domain.RecipeBom;
import com.naengjang_goat.inventory_system.menu.dto.MenuRequest;
import com.naengjang_goat.inventory_system.menu.dto.MenuResponse;
import com.naengjang_goat.inventory_system.menu.repository.MenuRepository;
import com.naengjang_goat.inventory_system.workflow.InventoryGate;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MenuService {
  private static final BigDecimal MAX_REQUIRED = new BigDecimal("9999999.999");
  private final MenuRepository menus;
  private final IngredientRepository ingredients;
  private final InventoryGate gate;
  private final UnitConverter units;

  public List<MenuResponse> list(Long userId) {
    return menus.findAllByUserIdWithBom(userId).stream().map(MenuResponse::from).toList();
  }

  @Transactional
  public MenuResponse create(Long userId, MenuRequest request) {
    var owner = gate.lock(userId);
    String name = request.name().trim();
    if (menus.existsByUserIdAndName(userId, name)) throw duplicateName();
    var recipe = normalizedRecipe(userId, request);
    var menu = new Menu(owner, name, request.price());
    replaceRecipe(menu, recipe);
    return MenuResponse.from(menus.saveAndFlush(menu));
  }

  @Transactional
  public MenuResponse update(Long userId, Long menuId, MenuRequest request) {
    gate.lock(userId);
    var menu = menus.findByIdWithBom(menuId)
        .filter(existing -> existing.getUser().getId().equals(userId))
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "메뉴를 찾을 수 없습니다."));
    String name = request.name().trim();
    if (menus.existsByUserIdAndNameAndIdNot(userId, name, menuId)) throw duplicateName();
    // 모든 재료·단위를 먼저 검증하여 잘못된 요청이 기존 레시피를 지우지 않도록 한다.
    var recipe = normalizedRecipe(userId, request);
    menu.setName(name);
    menu.setPrice(request.price());
    replaceRecipe(menu, recipe);
    menus.flush();
    return MenuResponse.from(menu);
  }

  private record NormalizedItem(Ingredient ingredient, BigDecimal quantity) {}

  private List<NormalizedItem> normalizedRecipe(Long userId, MenuRequest request) {
    var seen = new HashSet<Long>();
    var normalized = new ArrayList<NormalizedItem>();
    for (var item : request.recipe()) {
      if (!seen.add(item.ingredientId())) throw bad("같은 재료는 레시피에 한 번만 등록하세요.");
      var ingredient = ingredients.findById(item.ingredientId())
          .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "재료를 찾을 수 없습니다."));
      if (!ingredient.getUser().getId().equals(userId))
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "본인 매장의 재료만 사용할 수 있습니다.");
      BigDecimal quantity;
      try {
        quantity = units.convert(item.unit(), item.requiredQuantity(), ingredient.getBaseUnit());
      } catch (IllegalArgumentException exception) {
        throw bad("레시피 단위가 재료 기준 단위와 맞지 않습니다: " + ingredient.getName());
      }
      if (quantity.signum() <= 0 || quantity.compareTo(MAX_REQUIRED) > 0)
        throw bad("기준 단위로 환산한 소모량은 0.001~9999999.999 사이여야 합니다.");
      normalized.add(new NormalizedItem(ingredient, quantity));
    }
    return normalized;
  }

  private void replaceRecipe(Menu menu, List<NormalizedItem> recipe) {
    menu.getBom().clear();
    for (var item : recipe)
      menu.addBom(new RecipeBom(menu, item.ingredient(), item.quantity(), item.ingredient().getBaseUnit()));
  }

  private ResponseStatusException duplicateName() {
    return new ResponseStatusException(HttpStatus.CONFLICT, "이미 등록한 메뉴명입니다.");
  }

  private ResponseStatusException bad(String message) {
    return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
  }
}
