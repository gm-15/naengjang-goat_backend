package com.naengjang_goat.inventory_system.recipe.controller;

import com.naengjang_goat.inventory_system.recipe.domain.Recipe;
import com.naengjang_goat.inventory_system.recipe.service.RecipeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * [v2.1 비활성화]
 * 비활성화 사유: MenuController로 대체
 * 비활성화 일자: 2026-03-15
 */
// @RestController  // [v2.1 비활성화]
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
