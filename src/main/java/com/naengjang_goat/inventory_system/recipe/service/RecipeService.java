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

    // 전체 레시피 조회
    public List<Recipe> getAllRecipes() {
        return recipeRepository.findAll();
    }

    // 사용자별 레시피 조회
    public List<Recipe> getRecipesByUser(Long userId) {
        return recipeRepository.findAll().stream()
                .filter(recipe -> recipe.getUser().getId().equals(userId))
                .toList();
    }

    // 단일 레시피 조회
    public Recipe getRecipe(Long recipeId) {
        return recipeRepository.findById(recipeId)
                .orElseThrow(() -> new IllegalArgumentException("Recipe not found: " + recipeId));
    }

    // 레시피 구성 재료 조회
    public List<RecipeItem> getRecipeItems(Long recipeId) {
        return recipeItemRepository.findAllByRecipeId(recipeId);
    }
}
