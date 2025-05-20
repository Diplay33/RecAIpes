package com.ynov.recaipes.controller;

import com.ynov.recaipes.dto.RecipeRequest;
import com.ynov.recaipes.dto.RecipeResponse;
import com.ynov.recaipes.model.Recipe;
import com.ynov.recaipes.service.RecipeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/recipes")
@RequiredArgsConstructor
public class RecipeController {
    private final RecipeService recipeService;

    @PostMapping
    public ResponseEntity<RecipeResponse> generateRecipe(@RequestBody RecipeRequest request) {
        Recipe recipe = recipeService.generateRecipe(request);
        return ResponseEntity.ok(mapToResponse(recipe));
    }

    @GetMapping
    public ResponseEntity<List<RecipeResponse>> getAllRecipes() {
        List<RecipeResponse> recipes = recipeService.getAllRecipes().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(recipes);
    }

    @GetMapping("/{id}")
    public ResponseEntity<RecipeResponse> getRecipeById(@PathVariable Long id) {
        Recipe recipe = recipeService.getRecipeById(id);
        return ResponseEntity.ok(mapToResponse(recipe));
    }

    private RecipeResponse mapToResponse(Recipe recipe) {
        return RecipeResponse.builder()
                .id(recipe.getId())
                .title(recipe.getTitle())
                .description(recipe.getDescription())
                .ingredients(recipe.getIngredients())
                .instructions(recipe.getInstructions())
                .imageUrl(recipe.getImageUrl())
                .pdfUrl(recipe.getPdfUrl())
                .createdBy(recipe.getCreatedBy())
                .createdAt(recipe.getCreatedAt())
                .build();
    }
}