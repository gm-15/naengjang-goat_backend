package com.naengjang_goat.inventory_system.recipe.service;

import com.naengjang_goat.inventory_system.recipe.domain.Recipe;
import com.naengjang_goat.inventory_system.recipe.domain.RecipeItem;
import com.naengjang_goat.inventory_system.recipe.repository.RecipeItemRepository;
import com.naengjang_goat.inventory_system.recipe.repository.RecipeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RecipeService {

    private final RecipeRepository recipeRepository;
    private final RecipeItemRepository recipeItemRepository;

    public List<Recipe> getRecipesByUser(Long userId) {
        return recipeRepository.findAll().stream()
                .filter(recipe -> recipe.getUser().getId().equals(userId))
                .toList();
    }

    public List<RecipeItem> getRecipeItems(Long recipeId) {
        return recipeItemRepository.findAllByRecipeId(recipeId);
    }
}
