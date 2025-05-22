package com.ynov.recaipes.controller;

import com.ynov.recaipes.dto.RecipeRequest;
import com.ynov.recaipes.dto.RecipeResponse;
import com.ynov.recaipes.model.Recipe;
import com.ynov.recaipes.service.RecipeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
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

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteRecipe(@PathVariable Long id) {
        try {
            recipeService.deleteRecipe(id);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Recette supprimée avec succès",
                    "id", id
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", "Erreur lors de la suppression: " + e.getMessage()
            ));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<RecipeResponse> updateRecipe(@PathVariable Long id, @RequestBody RecipeRequest request) {
        try {
            Recipe updatedRecipe = recipeService.updateRecipe(id, request);
            return ResponseEntity.ok(mapToResponse(updatedRecipe));
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
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