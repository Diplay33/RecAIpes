// RecipeService.java
package com.ynov.recaipes.service;

import com.ynov.recaipes.dto.RecipeRequest;
import com.ynov.recaipes.model.Recipe;
import com.ynov.recaipes.model.PdfMetadata;
import com.ynov.recaipes.repository.RecipeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
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
            String recipeText = openAIService.generateRecipeText(
                    request.getIngredients(),
                    request.getDiet(),
                    request.getCuisine()
            );
            
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
        Pattern titlePattern = Pattern.compile("(?m)^(.+?)$");
        Matcher titleMatcher = titlePattern.matcher(recipeText);
        String title = titleMatcher.find() ? titleMatcher.group(1).trim() : "Recipe";
        
        Pattern ingredientsPattern = Pattern.compile("(?si)INGREDIENTS.*?(?=INSTRUCTIONS|$)");
        Matcher ingredientsMatcher = ingredientsPattern.matcher(recipeText);
        String ingredients = ingredientsMatcher.find() ? ingredientsMatcher.group(0).trim() : "";
        
        Pattern instructionsPattern = Pattern.compile("(?si)INSTRUCTIONS.*?(?=DESCRIPTION|$)");
        Matcher instructionsMatcher = instructionsPattern.matcher(recipeText);
        String instructions = instructionsMatcher.find() ? instructionsMatcher.group(0).trim() : "";
        
        Pattern descriptionPattern = Pattern.compile("(?si)DESCRIPTION.*$");
        Matcher descriptionMatcher = descriptionPattern.matcher(recipeText);
        String description = descriptionMatcher.find() ? descriptionMatcher.group(0).trim() : "";
        
        return Map.of(
            "title", title,
            "ingredients", ingredients,
            "instructions", instructions,
            "description", description
        );
    }
}