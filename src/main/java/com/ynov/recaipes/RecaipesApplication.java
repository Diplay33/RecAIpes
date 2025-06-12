package com.ynov.recaipes.service;

import com.ynov.recaipes.dto.RecipeRequest;
import com.ynov.recaipes.model.Recipe;
import com.ynov.recaipes.model.PdfMetadata;
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
						System.out.println("Reusing a recent recipe: " + mostRecent.getId());
						return mostRecent;
					}
				}

				String recipeText = openAIService.generateRecipeText(request.getDishName());
				System.out.println("Recipe generated: \n" + recipeText);

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
				System.out.println("Recipe generated successfully: " + recipe.getId() + " (request ID: " + requestId + ")");

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
			Set<String> filesToDelete = new HashSet<>();
			if (recipe.getImageUrl() != null && !recipe.getImageUrl().isEmpty()) {
				filesToDelete.add(recipe.getImageUrl());
			}
			if (recipe.getPdfUrl() != null && !recipe.getPdfUrl().isEmpty()) {
				filesToDelete.add(recipe.getPdfUrl());
			}

			PdfMetadata pdfMetadata = pdfMetadataRepository.findByRecipeId(id);
			if (pdfMetadata != null) {
				System.out.println("Deleting PDF metadata: " + pdfMetadata.getId());
				if (pdfMetadata.getS3Url() != null && !pdfMetadata.getS3Url().isEmpty()) {
					filesToDelete.add(pdfMetadata.getS3Url());
				}
				if (pdfMetadata.getLocalPath() != null && !pdfMetadata.getLocalPath().isEmpty()) {
					filesToDelete.add("file://" + pdfMetadata.getLocalPath());
				}
				pdfMetadataRepository.delete(pdfMetadata);
			}

			recipeRepository.delete(recipe);

			if (!filesToDelete.isEmpty()) {
				storageService.deleteFiles(new ArrayList<>(filesToDelete));
			}

			System.out.println("✅ Recipe deleted successfully: " + id);
		} catch (Exception e) {
			System.err.println("❌ Error deleting recipe " + id + ": " + e.getMessage());
			throw new RuntimeException("Could not delete recipe: " + e.getMessage(), e);
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
	 * This is the core method for parsing and cleaning the raw text from OpenAI.
	 * It's designed to be robust against common AI formatting inconsistencies.
	 */
	private Map<String, String> parseRecipeText(String recipeText) {
		System.out.println("Full recipe text:\n" + recipeText);

		// --- ROBUST FIX ---
		// First, sanitize the input by removing any potential invisible leading characters
		// like a Byte Order Mark (BOM) that can prevent regex patterns from matching
		// at the beginning of the string (using the '^' anchor).
		if (recipeText != null && !recipeText.isEmpty() && recipeText.startsWith("\uFEFF")) {
			recipeText = recipeText.substring(1);
			System.out.println("✅ BOM character removed from the beginning of the text.");
		}
		// --- END OF FIX ---

		String title = extractTitle(recipeText);
		String ingredients = extractSection(recipeText, "INGR[EÉ]DIENTS?", "INSTRUCTIONS|PREPARATION|ÉTAPES");
		String instructions = extractSection(recipeText, "INSTRUCTIONS?|PREPARATION|ÉTAPES", "DESCRIPTION");
		String description = extractSection(recipeText, "DESCRIPTION", null);

		if (title == null || title.trim().isEmpty()) {
			title = "Generated Recipe";
			System.out.println("⚠️ Extracted title is empty, using fallback: " + title);
		}
		if (ingredients == null || ingredients.trim().isEmpty()) {
			ingredients = "- Ingredients not specified";
		}
		if (instructions == null || instructions.trim().isEmpty()) {
			instructions = "1. Instructions not specified";
		}
		if (description == null || description.trim().isEmpty()) {
			description = "No description available.";
		}

		ingredients = cleanSectionText(ingredients, "INGR[EÉ]DIENTS?");
		instructions = cleanSectionText(instructions, "INSTRUCTIONS?|PREPARATION|ÉTAPES");
		description = cleanDescription(description);

		System.out.println("✅ Extracted Title: '" + title + "'");
		System.out.println("✅ Extracted Ingredients: " + ingredients.substring(0, Math.min(50, ingredients.length())) + "...");
		System.out.println("✅ Extracted Instructions: " + instructions.substring(0, Math.min(50, instructions.length())) + "...");
		System.out.println("✅ Extracted Description: " + description.substring(0, Math.min(50, description.length())) + "...");

		return Map.of(
				"title", cleanTitle(title),
				"ingredients", ingredients.trim(),
				"instructions", instructions.trim(),
				"description", description.trim()
		);
	}

	/**
	 * Intelligently extracts the recipe title using multiple patterns.
	 */
	private String extractTitle(String recipeText) {
		if (recipeText == null || recipeText.trim().isEmpty()) {
			return "Recipe Without Name";
		}
		String[] titlePatterns = {
				"(?i)^\\s*TITRE\\s*:?\\s*(.+?)$",
				"(?i)^\\s*RECIPE\\s*:?\\s*(.+?)$",
				"(?i)^\\s*NOM\\s*:?\\s*(.+?)$",
				"(?i)^\\s*#\\s*(.+?)$",
				"(?i)^\\s*\\*\\*(.+?)\\*\\*",
				"(?i)^\\s*(.+?)(?=\\n|INGR|DESCRIPTION)"
		};
		for (String patternStr : titlePatterns) {
			Pattern pattern = Pattern.compile(patternStr, Pattern.MULTILINE);
			Matcher matcher = pattern.matcher(recipeText);
			if (matcher.find()) {
				String candidateTitle = matcher.group(1).trim();
				if (!candidateTitle.isEmpty() && candidateTitle.length() <= 200 &&
						!candidateTitle.toLowerCase().contains("ingredient") &&
						!candidateTitle.toLowerCase().contains("instruction") &&
						!candidateTitle.toLowerCase().contains("description")) {
					System.out.println("🎯 Title extracted with pattern '" + patternStr + "': " + candidateTitle);
					return cleanTitle(candidateTitle);
				}
			}
		}
		String[] lines = recipeText.split("\\n");
		for (String line : lines) {
			String cleanLine = line.trim();
			if (!cleanLine.isEmpty() && cleanLine.length() > 3 && cleanLine.length() <= 200 &&
					!cleanLine.toLowerCase().startsWith("créé") &&
					!cleanLine.toLowerCase().startsWith("voici") &&
					!cleanLine.toLowerCase().startsWith("cette")) {
				System.out.println("🎯 Title extracted from first valid line: " + cleanLine);
				return cleanTitle(cleanLine);
			}
		}
		System.out.println("⚠️ No title found, using fallback");
		return "Delicious Recipe";
	}

	/**
	 * Cleans and formats the extracted title by removing unwanted prefixes.
	 */
	private String cleanTitle(String title) {
		if (title == null) {
			return "Recipe Without Name";
		}
		// This regex removes common OpenAI prefixes, case-insensitively.
		title = title.trim()
				.replaceAll("(?i)^(TITLE|TITRE|RECIPE|RECETTE|NOM)\\s*:?\\s*", "")
				.replaceAll("^[\\*#\\-\\s]+", "")
				.replaceAll("[\\*#\\-\\s]+$", "")
				.replaceAll("\\s+", " ");
		if (!title.isEmpty()) {
			title = title.substring(0, 1).toUpperCase() + (title.length() > 1 ? title.substring(1) : "");
		}
		return title.isEmpty() ? "Recipe Without Name" : title;
	}

	/**
	 * Cleans the description text, removing unwanted prefixes.
	 */
	private String cleanDescription(String description) {
		if (description == null || description.trim().isEmpty()) {
			return "No description available.";
		}
		description = description.trim()
				.replaceAll("(?i)^(DESCRIPTION|DESC)\\s*:?\\s*", "")
				.replaceAll("^:+\\s*", "")
				.trim();
		if (description.length() > 950) {
			description = description.substring(0, 947) + "...";
			System.out.println("⚠️ Description truncated to 950 characters");
		}
		return description.isEmpty() ? "No description available." : description;
	}

	/**
	 * Extracts a specific section (e.g., ingredients, instructions) from the recipe text.
	 */
	private String extractSection(String text, String startPattern, String endPattern) {
		if (text == null || text.isEmpty()) {
			return "";
		}
		try {
			Pattern pattern;
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
				return matcher.group(0).trim();
			}
		} catch (Exception e) {
			System.err.println("Error extracting section: " + e.getMessage());
		}
		return "";
	}

	/**
	 * Cleans an extracted section by removing its header (e.g., "INGREDIENTS:").
	 */
	private String cleanSectionText(String text, String... prefixes) {
		if (text == null || text.isEmpty()) {
			return "";
		}
		for (String prefix : prefixes) {
			if (prefix != null) {
				String pattern = "(?i)^(" + prefix + ")\\s*:?\\s*\\n?";
				text = text.replaceFirst(pattern, "").trim();
			}
		}
		text = text.replaceAll("^:+\\s*", "").trim();
		if (text.startsWith("\n:") || text.startsWith(": ")) {
			text = text.replaceFirst("^\\n?:\\s*", "").trim();
		}
		return text;
	}
}