package com.ynov.recaipes.controller;

import com.ynov.recaipes.model.Recipe;
import com.ynov.recaipes.service.BatchRecipeGenerationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/recipes/batch")
@RequiredArgsConstructor
public class BatchController {

    private final BatchRecipeGenerationService batchService;

    /**
     * Génère un menu complet (entrée, plat, dessert)
     */
    @PostMapping("/menu")
    public ResponseEntity<Map<String, Object>> generateMenu(@RequestBody MenuRequest request) {
        try {
            // Créer un nouvel ID de tâche
            String jobId = BatchStatusController.createNewJob();

            // Démarrer la génération de manière asynchrone
            CompletableFuture<List<Recipe>> future = batchService.generateCompleteMenu(
                    request.getUserName(),
                    request.getTheme()
            );

            // Mettre à jour la progression pendant la génération
            future.thenAccept(recipes -> {
                BatchStatusController.completeJob(jobId, "Menu généré avec succès");
            }).exceptionally(e -> {
                BatchStatusController.failJob(jobId, e.getMessage());
                return null;
            });

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Génération du menu démarrée",
                    "type", "menu",
                    "theme", request.getTheme(),
                    "jobId", jobId
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", "Erreur lors du démarrage de la génération: " + e.getMessage()
            ));
        }
    }

    /**
     * Génère des recettes par thème
     */
    @PostMapping("/theme")
    public ResponseEntity<Map<String, Object>> generateByTheme(@RequestBody ThemeRequest request) {
        try {
            CompletableFuture<List<Recipe>> future = batchService.generateThemeRecipes(
                    request.getUserName(),
                    request.getTheme(),
                    request.getCount()
            );

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Génération par thème démarrée",
                    "type", "theme",
                    "theme", request.getTheme(),
                    "count", request.getCount()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", "Erreur lors du démarrage de la génération: " + e.getMessage()
            ));
        }
    }

    /**
     * Génère des recettes personnalisées
     */
    @PostMapping("/custom")
    public ResponseEntity<Map<String, Object>> generateCustom(@RequestBody CustomRequest request) {
        try {
            CompletableFuture<List<Recipe>> future = batchService.generateCustomRecipes(
                    request.getUserName(),
                    request.getDishes()
            );

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Génération personnalisée démarrée",
                    "type", "custom",
                    "dishCount", request.getDishes().size()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", "Erreur lors du démarrage de la génération: " + e.getMessage()
            ));
        }
    }

    // DTOs pour les requêtes
    public static class MenuRequest {
        private String userName;
        private String theme;

        public String getUserName() { return userName; }
        public void setUserName(String userName) { this.userName = userName; }

        public String getTheme() { return theme; }
        public void setTheme(String theme) { this.theme = theme; }
    }

    public static class ThemeRequest {
        private String userName;
        private String theme;
        private int count = 4;

        public String getUserName() { return userName; }
        public void setUserName(String userName) { this.userName = userName; }

        public String getTheme() { return theme; }
        public void setTheme(String theme) { this.theme = theme; }

        public int getCount() { return count; }
        public void setCount(int count) { this.count = count; }
    }

    public static class CustomRequest {
        private String userName;
        private List<String> dishes;

        public String getUserName() { return userName; }
        public void setUserName(String userName) { this.userName = userName; }

        public List<String> getDishes() { return dishes; }
        public void setDishes(List<String> dishes) { this.dishes = dishes; }
    }
}