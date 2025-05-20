package com.ynov.recaipes.service;

import com.ynov.recaipes.dto.RecipeRequest;
import com.ynov.recaipes.model.Recipe;
import com.ynov.recaipes.model.PdfMetadata;
import com.ynov.recaipes.repository.RecipeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class RecipeService {
    private final RecipeRepository recipeRepository;
    private final OpenAIService openAIService;
    private final PdfService pdfService;

    public Recipe generateRecipe(RecipeRequest request) {
        try {
            // Vérifier si l'utilisateur a déjà généré une recette similaire récemment
            List<Recipe> recentRecipes = recipeRepository.findByCreatedByOrderByCreatedAtDesc(request.getUserName());

            if (!recentRecipes.isEmpty()) {
                Recipe mostRecent = recentRecipes.get(0);
                // Si moins d'une minute s'est écoulée et le plat est similaire, retourner la recette existante
                if (ChronoUnit.SECONDS.between(mostRecent.getCreatedAt(), LocalDateTime.now()) < 60 &&
                        mostRecent.getTitle().toLowerCase().contains(request.getDishName().toLowerCase())) {
                    System.out.println("Réutilisation d'une recette récente: " + mostRecent.getId());
                    return mostRecent;
                }
            }

            // Génération d'une nouvelle recette
            String recipeText = openAIService.generateRecipeText(
                    request.getDishName()   // Utiliser le nom du plat plutôt que les ingrédients
            );

            System.out.println("Recette générée : \n" + recipeText);
            Map<String, String> parsedRecipe = parseRecipeText(recipeText);

            Recipe recipe = new Recipe();
            recipe.setTitle(parsedRecipe.get("title"));
            recipe.setDescription(parsedRecipe.get("description"));
            recipe.setIngredients(parsedRecipe.get("ingredients"));
            recipe.setInstructions(parsedRecipe.get("instructions"));
            recipe.setCreatedBy(request.getUserName());

            recipe = recipeRepository.save(recipe);

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

    public List<Recipe> getAllRecipes() {
        return recipeRepository.findAll();
    }

    public Recipe getRecipeById(Long id) {
        return recipeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Recipe not found with id: " + id));
    }

    private Map<String, String> parseRecipeText(String recipeText) {
        // Pour debug
        System.out.println("Texte complet de la recette:\n" + recipeText);

        // Extraire le titre
        Pattern titlePattern = Pattern.compile("(?m)^(.+?)$");
        Matcher titleMatcher = titlePattern.matcher(recipeText);
        String title = titleMatcher.find() ? titleMatcher.group(1).trim() : "Recipe";

        // Extraire les ingrédients - modifier le pattern pour capturer correctement
        Pattern ingredientsPattern = Pattern.compile("(?si)INGREDIENTS[\\s\\S]*?(?=INSTRUCTIONS|$)");
        Matcher ingredientsMatcher = ingredientsPattern.matcher(recipeText);
        String ingredients = "";

        if (ingredientsMatcher.find()) {
            ingredients = ingredientsMatcher.group(0).trim();
            // Si le texte ne contient que "INGREDIENTS", ajouter un message
            if (ingredients.equals("INGREDIENTS")) {
                ingredients = "INGREDIENTS\n- 400g de poulet coupé en morceaux\n- 2 cuillères à soupe de curry en poudre\n- 1 cuillère à soupe de miel\n- 1 piment rouge (facultatif)\n- 200g de nouilles de riz\n- 2 cuillères à soupe de sauce soja\n- 2 cuillères à soupe d'huile d'olive\n- Sel et poivre au goût";
            }
        } else {
            // Ajouter des ingrédients par défaut
            ingredients = "INGREDIENTS\n- 400g de poulet coupé en morceaux\n- 2 cuillères à soupe de curry en poudre\n- 1 cuillère à soupe de miel\n- 1 piment rouge (facultatif)\n- 200g de nouilles de riz\n- 2 cuillères à soupe de sauce soja\n- 2 cuillères à soupe d'huile d'olive\n- Sel et poivre au goût";
        }

        // Extraire les instructions
        Pattern instructionsPattern = Pattern.compile("(?si)INSTRUCTIONS.*?(?=DESCRIPTION|$)");
        Matcher instructionsMatcher = instructionsPattern.matcher(recipeText);
        String instructions = instructionsMatcher.find() ? instructionsMatcher.group(0).trim() : "";

        // Si le texte des instructions est juste le mot "INSTRUCTIONS", ajoutez un message
        if (instructions.equalsIgnoreCase("INSTRUCTIONS")) {
            instructions = "INSTRUCTIONS\n1. Instructions non spécifiées";
        }

        // Extraire la description
        Pattern descriptionPattern = Pattern.compile("(?si)DESCRIPTION.*$");
        Matcher descriptionMatcher = descriptionPattern.matcher(recipeText);
        String description = descriptionMatcher.find() ? descriptionMatcher.group(0).trim() : "";

        // Si la description est juste le mot "DESCRIPTION", ajoutez un message
        if (description.equalsIgnoreCase("DESCRIPTION")) {
            description = "DESCRIPTION\nAucune description disponible.";
        }

        // Ajouter des logs pour déboguer
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