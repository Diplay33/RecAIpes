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
    private final StorageService storageService;

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

            // Collecter les URLs uniques des fichiers à supprimer
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

    /**
     * Méthode améliorée pour analyser le texte de recette généré par OpenAI
     * Cette version utilise des techniques d'extraction plus robustes pour identifier
     * correctement le titre de la recette plutôt que de simplement prendre la première ligne
     */
    private Map<String, String> parseRecipeText(String recipeText) {
        System.out.println("Texte complet de la recette:\n" + recipeText);

        // Extraction du titre avec une logique améliorée
        String title = extractTitle(recipeText);

        // Extraction des sections avec des patterns regex plus précis
        String ingredients = extractSection(recipeText, "INGR[EÉ]DIENTS?", "INSTRUCTIONS|PREPARATION|ÉTAPES");
        String instructions = extractSection(recipeText, "INSTRUCTIONS?|PREPARATION|ÉTAPES", "DESCRIPTION");
        String description = extractSection(recipeText, "DESCRIPTION", null);

        // Validation et fallbacks pour garantir qu'on a toujours du contenu valide
        if (title == null || title.trim().isEmpty()) {
            title = "Recette Générée";
            System.out.println("⚠️ Titre extrait vide, utilisation du fallback: " + title);
        }

        if (ingredients == null || ingredients.trim().isEmpty()) {
            ingredients = "INGRÉDIENTS\n- Ingrédients non spécifiés";
        }

        if (instructions == null || instructions.trim().isEmpty()) {
            instructions = "INSTRUCTIONS\n1. Instructions non spécifiées";
        }

        if (description == null || description.trim().isEmpty()) {
            description = "DESCRIPTION\nAucune description disponible.";
        }

        System.out.println("✅ Titre extrait: '" + title + "'");
        System.out.println("✅ Ingrédients extraits: " + ingredients.substring(0, Math.min(50, ingredients.length())) + "...");
        System.out.println("✅ Instructions extraites: " + instructions.substring(0, Math.min(50, instructions.length())) + "...");
        System.out.println("✅ Description extraite: " + description.substring(0, Math.min(50, description.length())) + "...");

        return Map.of(
                "title", title.trim(),
                "ingredients", ingredients.trim(),
                "instructions", instructions.trim(),
                "description", description.trim()
        );
    }

    /**
     * Extraction intelligente du titre de la recette
     * Cette méthode essaie plusieurs patterns pour identifier le vrai titre
     * plutôt que de simplement prendre la première ligne
     */
    private String extractTitle(String recipeText) {
        if (recipeText == null || recipeText.trim().isEmpty()) {
            return "Recette Sans Nom";
        }

        // Patterns pour identifier le titre dans différents formats possibles
        String[] titlePatterns = {
                "(?i)^\\s*TITRE\\s*:?\\s*(.+?)$",           // Format: TITRE: Nom de la recette
                "(?i)^\\s*RECIPE\\s*:?\\s*(.+?)$",          // Format: RECIPE: Nom de la recette
                "(?i)^\\s*NOM\\s*:?\\s*(.+?)$",             // Format: NOM: Nom de la recette
                "(?i)^\\s*#\\s*(.+?)$",                     // Format: # Nom de la recette
                "(?i)^\\s*\\*\\*(.+?)\\*\\*",               // Format: **Nom de la recette**
                "(?i)^\\s*(.+?)(?=\\n|INGR|DESCRIPTION)"   // Première ligne avant les sections
        };

        // Essayer chaque pattern pour trouver un titre valide
        for (String patternStr : titlePatterns) {
            Pattern pattern = Pattern.compile(patternStr, Pattern.MULTILINE);
            Matcher matcher = pattern.matcher(recipeText);

            if (matcher.find()) {
                String candidateTitle = matcher.group(1).trim();

                // Valider que ce candidat est vraiment un titre (pas trop long, pas de mots-clés de section)
                if (!candidateTitle.isEmpty() &&
                        candidateTitle.length() <= 200 &&
                        !candidateTitle.toLowerCase().contains("ingrédient") &&
                        !candidateTitle.toLowerCase().contains("instruction") &&
                        !candidateTitle.toLowerCase().contains("description")) {

                    System.out.println("🎯 Titre extrait avec pattern '" + patternStr + "': " + candidateTitle);
                    return cleanTitle(candidateTitle);
                }
            }
        }

        // Si aucun pattern spécifique n'a fonctionné, chercher la première ligne significative
        String[] lines = recipeText.split("\\n");
        for (String line : lines) {
            String cleanLine = line.trim();
            if (!cleanLine.isEmpty() &&
                    cleanLine.length() > 3 &&
                    cleanLine.length() <= 200 &&
                    !cleanLine.toLowerCase().startsWith("créé") &&
                    !cleanLine.toLowerCase().startsWith("voici") &&
                    !cleanLine.toLowerCase().startsWith("cette")) {

                System.out.println("🎯 Titre extrait depuis première ligne valide: " + cleanLine);
                return cleanTitle(cleanLine);
            }
        }

        System.out.println("⚠️ Aucun titre trouvé, utilisation du fallback");
        return "Recette Délicieuse";
    }

    /**
     * Nettoie et formate le titre extrait pour qu'il soit présentable
     * Supprime les caractères indésirables et normalise le formatage
     */
    private String cleanTitle(String title) {
        if (title == null) {
            return "Recette Sans Nom";
        }

        // Nettoyage des caractères de formatage en début et fin
        title = title.trim()
                .replaceAll("^[\\*#\\-\\s]+", "")  // Supprimer *, #, -, espaces en début
                .replaceAll("[\\*#\\-\\s]+$", "")  // Supprimer *, #, -, espaces en fin
                .replaceAll("\\s+", " ");          // Normaliser les espaces multiples

        // Capitaliser la première lettre si nécessaire
        if (!title.isEmpty()) {
            title = title.substring(0, 1).toUpperCase() +
                    (title.length() > 1 ? title.substring(1) : "");
        }

        return title.isEmpty() ? "Recette Sans Nom" : title;
    }

    /**
     * Extrait une section spécifique du texte de recette en utilisant des patterns regex
     * Plus précis que la méthode originale qui pouvait manquer du contenu
     */
    private String extractSection(String text, String startPattern, String endPattern) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        try {
            Pattern pattern;

            // Construire le pattern regex selon qu'on a un pattern de fin ou non
            if (endPattern != null) {
                pattern = Pattern.compile(
                        "(?si)(" + startPattern + ").*?(?=" + endPattern + "|$)",
                        Pattern.MULTILINE | Pattern.DOTALL
                );
            } else {
                pattern = Pattern.compile(
                        "(?si)(" + startPattern + ").*$",
                        Pattern.MULTILINE | Pattern.DOTALL
                );
            }

            Matcher matcher = pattern.matcher(text);

            if (matcher.find()) {
                String section = matcher.group(0).trim();

                // Nettoyer la section en supprimant le header redondant
                section = section.replaceAll("(?i)^(INGR[EÉ]DIENTS?|INSTRUCTIONS?|PREPARATION|ÉTAPES|DESCRIPTION)\\s*:?\\s*", "");

                return section.trim();
            }
        } catch (Exception e) {
            System.err.println("Erreur lors de l'extraction de section: " + e.getMessage());
        }

        return "";
    }
}