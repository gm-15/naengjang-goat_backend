package com.naengjang_goat.inventory_system.recipe.repository;

import com.naengjang_goat.inventory_system.recipe.Recipe;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RecipeRepository extends JpaRepository<Recipe, Long> {
    List<Recipe> findAllByNameContaining(String name);
}
