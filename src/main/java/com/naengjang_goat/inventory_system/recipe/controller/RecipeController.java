package com.naengjang_goat.inventory_system.recipe.controller;

import com.naengjang_goat.inventory_system.recipe.domain.Recipe;
import com.naengjang_goat.inventory_system.recipe.service.RecipeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/recipes")
@RequiredArgsConstructor
public class RecipeController {

    private final RecipeService recipeService;

    // 전체 레시피 조회
    @GetMapping
    public List<Recipe> getAll() {
        return recipeService.getAllRecipes();
    }

    // 단일 레시피 조회
    @GetMapping("/{id}")
    public Recipe getOne(@PathVariable Long id) {
        return recipeService.getRecipe(id);
    }
}
