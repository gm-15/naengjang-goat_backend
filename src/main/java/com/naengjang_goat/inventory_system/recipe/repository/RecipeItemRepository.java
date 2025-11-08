package com.naengjang_goat.inventory_system.recipe.repository;

import com.naengjang_goat.inventory_system.recipe.RecipeItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RecipeItemRepository extends JpaRepository<RecipeItem, Long> {
    List<RecipeItem> findAllByRecipeId(Long recipeId);
}
