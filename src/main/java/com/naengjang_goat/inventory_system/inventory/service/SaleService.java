package com.naengjang_goat.inventory_system.inventory.service;

import com.naengjang_goat.inventory_system.inventory.domain.Inventory;
import com.naengjang_goat.inventory_system.inventory.domain.SaleHistory;
import com.naengjang_goat.inventory_system.inventory.repository.InventoryRepository;
import com.naengjang_goat.inventory_system.inventory.repository.SaleHistoryRepository;
import com.naengjang_goat.inventory_system.recipe.domain.Recipe;
import com.naengjang_goat.inventory_system.recipe.domain.RecipeItem;
import com.naengjang_goat.inventory_system.recipe.repository.RecipeItemRepository;
import com.naengjang_goat.inventory_system.recipe.repository.RecipeRepository;
import com.naengjang_goat.inventory_system.user.domain.User;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SaleService {

    private final RecipeRepository recipeRepository;
    private final RecipeItemRepository recipeItemRepository;
    private final InventoryRepository inventoryRepository;
    private final SaleHistoryRepository saleHistoryRepository;

    @Transactional
    public void processSale(User user, Long recipeId, int quantity) {
        Recipe recipe = recipeRepository.findById(recipeId)
                .orElseThrow(() -> new IllegalArgumentException("Recipe not found"));

        List<RecipeItem> recipeItems = recipeItemRepository.findAllByRecipeId(recipeId);

        for (RecipeItem item : recipeItems) {
            Inventory inventory = inventoryRepository.findByRawMaterialId(item.getRawMaterial().getId())
                    .orElseThrow(() -> new IllegalArgumentException("No inventory found for raw material"));

            double usedQty = item.getQuantity() * quantity;
            double newStock = inventory.getStockQuantity() - usedQty;

            if (newStock < 0)
                throw new IllegalStateException("재고 부족: " + item.getRawMaterial().getName());

            inventory.setStockQuantity(newStock);
            inventoryRepository.save(inventory);
        }

        SaleHistory sale = new SaleHistory();
        sale.setUser(user);
        sale.setSaleTimestamp(LocalDateTime.now());
        sale.setTotalAmount(recipe.getPrice() * quantity);

        saleHistoryRepository.save(sale);
    }
}
