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

    /**
     * Créer/Générer une nouvelle recette
     */
    @PostMapping
    public ResponseEntity<RecipeResponse> generateRecipe(@RequestBody RecipeRequest request) {
        try {
            Recipe recipe = recipeService.generateRecipe(request);
            return ResponseEntity.ok(mapToResponse(recipe));
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Obtenir toutes les recettes
     */
    @GetMapping
    public ResponseEntity<List<RecipeResponse>> getAllRecipes() {
        try {
            List<RecipeResponse> recipes = recipeService.getAllRecipes().stream()
                    .map(this::mapToResponse)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(recipes);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Obtenir une recette par son ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<RecipeResponse> getRecipeById(@PathVariable Long id) {
        try {
            Recipe recipe = recipeService.getRecipeById(id);
            return ResponseEntity.ok(mapToResponse(recipe));
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Modifier une recette existante
     */
    @PutMapping("/{id}")
    public ResponseEntity<RecipeResponse> updateRecipe(@PathVariable Long id, @RequestBody RecipeRequest request) {
        try {
            Recipe updatedRecipe = recipeService.updateRecipe(id, request);
            return ResponseEntity.ok(mapToResponse(updatedRecipe));
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Supprimer une recette
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteRecipe(@PathVariable Long id) {
        try {
            recipeService.deleteRecipe(id);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Recette supprimée avec succès",
                    "id", id
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", "Erreur lors de la suppression: " + e.getMessage()
            ));
        }
    }

    /**
     * Rechercher des recettes par nom d'utilisateur
     */
    @GetMapping("/user/{userName}")
    public ResponseEntity<List<RecipeResponse>> getRecipesByUser(@PathVariable String userName) {
        try {
            List<RecipeResponse> recipes = recipeService.getRecipesByUser(userName).stream()
                    .map(this::mapToResponse)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(recipes);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Obtenir les statistiques des recettes
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getRecipeStats() {
        try {
            Map<String, Object> stats = recipeService.getRecipeStats();
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Mapper une entité Recipe vers RecipeResponse
     */
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