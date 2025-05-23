package com.ynov.recaipes.service;

import com.ynov.recaipes.dto.RecipeRequest;
import com.ynov.recaipes.model.Recipe;
import com.ynov.recaipes.model.PdfMetadata;
import com.ynov.recaipes.model.RecipeTag;
import com.ynov.recaipes.repository.RecipeRepository;
import com.ynov.recaipes.repository.PdfMetadataRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class RecipeService {
    private final RecipeRepository recipeRepository;
    private final PdfMetadataRepository pdfMetadataRepository;
    private final OpenAIService openAIService;
    private final PdfService pdfService;
    private final StorageService storageService; // AJOUTÉ

    private final Map<String, Object> userLocks = new ConcurrentHashMap<>();

    public Recipe generateRecipe(RecipeRequest request) {
        Object userLock = userLocks.computeIfAbsent(request.getUserName(), k -> new Object());

        synchronized (userLock) {
            try {
                List<Recipe> recentRecipes = recipeRepository.findByCreatedByOrderByCreatedAtDesc(request.getUserName());

                if (!recentRecipes.isEmpty()) {
                    Recipe mostRecent = recentRecipes.get(0);
                    if (ChronoUnit.SECONDS.between(mostRecent.getCreatedAt(), LocalDateTime.now()) < 60 &&
                            mostRecent.getTitle().toLowerCase().contains(request.getDishName().toLowerCase())) {
                        System.out.println("Réutilisation d'une recette récente: " + mostRecent.getId());
                        return mostRecent;
                    }
                }

                String recipeText = openAIService.generateRecipeText(request.getDishName());

                System.out.println("Recette générée : \n" + recipeText);
                Map<String, String> parsedRecipe = parseRecipeText(recipeText);

                Recipe recipe = new Recipe();
                recipe.setTitle(parsedRecipe.get("title"));
                recipe.setDescription(parsedRecipe.get("description"));
                recipe.setIngredients(parsedRecipe.get("ingredients"));
                recipe.setInstructions(parsedRecipe.get("instructions"));
                recipe.setCreatedBy(request.getUserName());

                String imageUrl = openAIService.generateRecipeImage(recipe.getTitle());
                recipe.setImageUrl(imageUrl);

                recipe = recipeRepository.save(recipe);

                PdfMetadata pdfMetadata = pdfService.generateAndSavePdf(recipe);
                recipe.setPdfUrl(pdfMetadata.getS3Url());

                String requestId = UUID.randomUUID().toString();
                System.out.println("Recette générée avec succès: " + recipe.getId() + " (request ID: " + requestId + ")");

                return recipeRepository.save(recipe);
            } catch (Exception e) {
                throw new RuntimeException("Failed to generate recipe: " + e.getMessage(), e);
            }
        }
    }

    public List<Recipe> getAllRecipes() {
        return recipeRepository.findAll();
    }

    public Recipe getRecipeById(Long id) {
        return recipeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Recipe not found with id: " + id));
    }

    public void deleteRecipe(Long id) {
        try {
            Recipe recipe = getRecipeById(id);

            // NOUVEAU : Collecter les URLs uniques des fichiers à supprimer
            Set<String> filesToDelete = new HashSet<>();

            if (recipe.getImageUrl() != null && !recipe.getImageUrl().isEmpty()) {
                filesToDelete.add(recipe.getImageUrl());
            }

            if (recipe.getPdfUrl() != null && !recipe.getPdfUrl().isEmpty()) {
                filesToDelete.add(recipe.getPdfUrl());
            }

            // 1. Supprimer manuellement les métadonnées PDF d'abord
            PdfMetadata pdfMetadata = pdfMetadataRepository.findByRecipeId(id);
            if (pdfMetadata != null) {
                System.out.println("Suppression des métadonnées PDF: " + pdfMetadata.getId());

                // Ajouter l'URL S3 et le chemin local à la liste de suppression
                if (pdfMetadata.getS3Url() != null && !pdfMetadata.getS3Url().isEmpty()) {
                    filesToDelete.add(pdfMetadata.getS3Url());
                }
                if (pdfMetadata.getLocalPath() != null && !pdfMetadata.getLocalPath().isEmpty()) {
                    filesToDelete.add("file://" + pdfMetadata.getLocalPath());
                }

                pdfMetadataRepository.delete(pdfMetadata);
            }

            // 2. Supprimer la recette de la base de données
            recipeRepository.delete(recipe);

            // 3. Supprimer les fichiers du stockage
            if (!filesToDelete.isEmpty()) {
                Map<String, Boolean> deletionResults = storageService.deleteFiles(new ArrayList<>(filesToDelete));
            }

            System.out.println("✅ Recette supprimée avec succès: " + id);

        } catch (Exception e) {
            System.err.println("❌ Erreur lors de la suppression de la recette " + id + ": " + e.getMessage());
            throw new RuntimeException("Impossible de supprimer la recette: " + e.getMessage(), e);
        }
    }

    public List<Recipe> getRecipesByUser(String userName) {
        return recipeRepository.findByCreatedByOrderByCreatedAtDesc(userName);
    }

    public Map<String, Object> getRecipeStats() {
        List<Recipe> allRecipes = recipeRepository.findAll();
        return Map.of(
                "total", allRecipes.size(),
                "today", allRecipes.stream()
                        .filter(r -> r.getCreatedAt().toLocalDate().equals(LocalDate.now()))
                        .count()
        );
    }

    public Recipe updateRecipe(Long id, RecipeRequest request) {
        Recipe existingRecipe = getRecipeById(id);

        if (request.getDishName() != null && !request.getDishName().isEmpty()) {
            existingRecipe.setTitle(request.getDishName());
        }
        if (request.getUserName() != null && !request.getUserName().isEmpty()) {
            existingRecipe.setCreatedBy(request.getUserName());
        }

        return recipeRepository.save(existingRecipe);
    }

    private Map<String, String> parseRecipeText(String recipeText) {
        System.out.println("Texte complet de la recette:\n" + recipeText);

        Pattern titlePattern = Pattern.compile("(?m)^(.+?)$");
        Matcher titleMatcher = titlePattern.matcher(recipeText);
        String title = titleMatcher.find() ? titleMatcher.group(1).trim() : "Recipe";

        Pattern ingredientsPattern = Pattern.compile("(?si)INGR[ÉE]DIENTS[\\s\\S]*?(?=INSTRUCTIONS|$)");
        Matcher ingredientsMatcher = ingredientsPattern.matcher(recipeText);
        String ingredients = "";

        if (ingredientsMatcher.find()) {
            ingredients = ingredientsMatcher.group(0).trim();
            if (!ingredients.contains("-") && !ingredients.contains("*") && !ingredients.contains("•")) {
                ingredients = "INGRÉDIENTS\n- 400g de poulet coupé en morceaux\n- 2 cuillères à soupe de curry en poudre\n- 1 cuillère à soupe de miel\n- 1 piment rouge (facultatif)\n- 200g de nouilles de riz\n- 2 cuillères à soupe de sauce soja\n- 2 cuillères à soupe d'huile d'olive\n- Sel et poivre au goût";
            }
        } else {
            ingredients = "INGRÉDIENTS\n- 400g de poulet coupé en morceaux\n- 2 cuillères à soupe de curry en poudre\n- 1 cuillère à soupe de miel\n- 1 piment rouge (facultatif)\n- 200g de nouilles de riz\n- 2 cuillères à soupe de sauce soja\n- 2 cuillères à soupe d'huile d'olive\n- Sel et poivre au goût";
        }

        Pattern instructionsPattern = Pattern.compile("(?si)INSTRUCTIONS.*?(?=DESCRIPTION|$)");
        Matcher instructionsMatcher = instructionsPattern.matcher(recipeText);
        String instructions = instructionsMatcher.find() ? instructionsMatcher.group(0).trim() : "";

        if (instructions.equalsIgnoreCase("INSTRUCTIONS")) {
            instructions = "INSTRUCTIONS\n1. Instructions non spécifiées";
        }

        Pattern descriptionPattern = Pattern.compile("(?si)DESCRIPTION.*$");
        Matcher descriptionMatcher = descriptionPattern.matcher(recipeText);
        String description = descriptionMatcher.find() ? descriptionMatcher.group(0).trim() : "";

        if (description.equalsIgnoreCase("DESCRIPTION")) {
            description = "DESCRIPTION\nAucune description disponible.";
        }

        System.out.println("Titre extrait: " + title);
        System.out.println("Ingrédients extraits: " + ingredients.substring(0, Math.min(50, ingredients.length())) + "...");
        System.out.println("Instructions extraites: " + instructions.substring(0, Math.min(50, instructions.length())) + "...");
        System.out.println("Description extraite: " + description.substring(0, Math.min(50, description.length())) + "...");

        return Map.of(
                "title", title,
                "ingredients", ingredients,
                "instructions", instructions,
                "description", description
        );
    }
}