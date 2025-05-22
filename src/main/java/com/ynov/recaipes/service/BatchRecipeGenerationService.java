package com.ynov.recaipes.service;

import com.ynov.recaipes.dto.RecipeRequest;
import com.ynov.recaipes.model.Recipe;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class BatchRecipeGenerationService {

    private final RecipeService recipeService;

    /**
     * Génère plusieurs recettes en chaîne de façon asynchrone
     */
    @Async
    public CompletableFuture<List<Recipe>> generateRecipeBatch(BatchGenerationRequest request) {
        List<Recipe> generatedRecipes = new ArrayList<>();

        try {
            System.out.println("Démarrage de la génération en chaîne de " +
                    request.getRecipeRequests().size() + " recettes");

            for (int i = 0; i < request.getRecipeRequests().size(); i++) {
                RecipeRequest recipeRequest = request.getRecipeRequests().get(i);

                System.out.println("Génération de la recette " + (i + 1) + "/" +
                        request.getRecipeRequests().size() + " : " +
                        recipeRequest.getDishName());

                // Générer la recette
                Recipe recipe = recipeService.generateRecipe(recipeRequest);

                // Ajouter les tags spécifiques au batch
                recipe.addTag("tag1", request.getBatchType(), "Type de batch");
                recipe.addTag("tag2", "batch-" + request.getBatchId(), "ID du batch");
                recipe.addTag("tag3", String.valueOf(i + 1), "Position dans le batch");

                generatedRecipes.add(recipe);

                // Délai entre les générations pour éviter la surcharge de l'API
                if (i < request.getRecipeRequests().size() - 1) {
                    Thread.sleep(TimeUnit.SECONDS.toMillis(request.getDelayBetweenRequests()));
                }
            }

            System.out.println("Génération en chaîne terminée avec succès ! " +
                    generatedRecipes.size() + " recettes créées");

        } catch (Exception e) {
            System.err.println("Erreur lors de la génération en chaîne : " + e.getMessage());
            throw new RuntimeException("Batch generation failed", e);
        }

        return CompletableFuture.completedFuture(generatedRecipes);
    }

    /**
     * Génère un menu complet (entrée, plat, dessert)
     */
    @Async
    public CompletableFuture<List<Recipe>> generateCompleteMenu(String userName, String theme) {
        List<RecipeRequest> menuRequests = new ArrayList<>();

        // Créer les requêtes pour un menu complet - CORRIGÉ
        menuRequests.add(new RecipeRequest("Entrée " + theme, userName));
        menuRequests.add(new RecipeRequest("Plat principal " + theme, userName));
        menuRequests.add(new RecipeRequest("Dessert " + theme, userName));

        BatchGenerationRequest batchRequest = new BatchGenerationRequest();
        batchRequest.setRecipeRequests(menuRequests);
        batchRequest.setBatchType("menu-" + theme.toLowerCase().replace(" ", "-"));
        batchRequest.setBatchId(System.currentTimeMillis());
        batchRequest.setDelayBetweenRequests(30); // 30 secondes entre chaque recette

        return generateRecipeBatch(batchRequest);
    }

    /**
     * Génère des recettes par thème (ex: cuisine italienne)
     */
    @Async
    public CompletableFuture<List<Recipe>> generateThemeRecipes(String userName, String theme, int count) {
        List<RecipeRequest> themeRequests = new ArrayList<>();

        // Plats typiques selon le thème
        List<String> dishes = getThemeDishes(theme, count);

        for (String dish : dishes) {
            themeRequests.add(new RecipeRequest(dish, userName));
        }

        BatchGenerationRequest batchRequest = new BatchGenerationRequest();
        batchRequest.setRecipeRequests(themeRequests);
        batchRequest.setBatchType("theme-" + theme.toLowerCase().replace(" ", "-"));
        batchRequest.setBatchId(System.currentTimeMillis());
        batchRequest.setDelayBetweenRequests(25);

        return generateRecipeBatch(batchRequest);
    }

    /**
     * Génère des recettes personnalisées
     */
    @Async
    public CompletableFuture<List<Recipe>> generateCustomRecipes(String userName, List<String> dishes) {
        List<RecipeRequest> customRequests = new ArrayList<>();

        for (String dish : dishes) {
            if (dish != null && !dish.trim().isEmpty()) {
                customRequests.add(new RecipeRequest(dish.trim(), userName));
            }
        }

        BatchGenerationRequest batchRequest = new BatchGenerationRequest();
        batchRequest.setRecipeRequests(customRequests);
        batchRequest.setBatchType("custom");
        batchRequest.setBatchId(System.currentTimeMillis());
        batchRequest.setDelayBetweenRequests(20);

        return generateRecipeBatch(batchRequest);
    }

    private List<String> getThemeDishes(String theme, int count) {
        List<String> dishes = new ArrayList<>();

        switch (theme.toLowerCase()) {
            case "italien":
                dishes.add("Pasta carbonara");
                dishes.add("Pizza margherita");
                dishes.add("Risotto aux champignons");
                dishes.add("Tiramisu");
                break;
            case "français":
                dishes.add("Coq au vin");
                dishes.add("Ratatouille");
                dishes.add("Crème brûlée");
                dishes.add("Bouillabaisse");
                break;
            case "asiatique":
                dishes.add("Pad Thai");
                dishes.add("Sushi");
                dishes.add("Curry vert");
                dishes.add("Ramen");
                break;
            default:
                dishes.add("Plat traditionnel " + theme);
                dishes.add("Spécialité " + theme);
                dishes.add("Dessert " + theme);
        }

        return dishes.subList(0, Math.min(count, dishes.size()));
    }

    /**
     * Classe pour encapsuler les paramètres de génération en batch
     */
    public static class BatchGenerationRequest {
        private List<RecipeRequest> recipeRequests;
        private String batchType;
        private Long batchId;
        private int delayBetweenRequests = 20; // secondes

        // Getters et setters
        public List<RecipeRequest> getRecipeRequests() { return recipeRequests; }
        public void setRecipeRequests(List<RecipeRequest> recipeRequests) { this.recipeRequests = recipeRequests; }

        public String getBatchType() { return batchType; }
        public void setBatchType(String batchType) { this.batchType = batchType; }

        public Long getBatchId() { return batchId; }
        public void setBatchId(Long batchId) { this.batchId = batchId; }

        public int getDelayBetweenRequests() { return delayBetweenRequests; }
        public void setDelayBetweenRequests(int delayBetweenRequests) { this.delayBetweenRequests = delayBetweenRequests; }
    }
}