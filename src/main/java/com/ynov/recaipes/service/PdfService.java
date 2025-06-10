package com.ynov.recaipes.service;

import com.ynov.recaipes.model.PdfMetadata;
import com.ynov.recaipes.model.Recipe;
import com.ynov.recaipes.repository.PdfMetadataRepository;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PdfService {
    private final PdfMetadataRepository pdfMetadataRepository;
    private final StorageService storageService;

    // Constantes pour la mise en page
    private static final float MARGIN = 50;
    private static final float PAGE_WIDTH = PDRectangle.A4.getWidth();
    private static final float PAGE_HEIGHT = PDRectangle.A4.getHeight();
    private static final float TEXT_WIDTH = PAGE_WIDTH - (2 * MARGIN);
    private static final float TOP_MARGIN = PAGE_HEIGHT - MARGIN;
    private static final float BOTTOM_MARGIN = MARGIN + 30;
    private static final PDType1Font TITLE_FONT = PDType1Font.HELVETICA_BOLD;
    private static final PDType1Font SECTION_FONT = PDType1Font.HELVETICA_BOLD;
    private static final PDType1Font TEXT_FONT = PDType1Font.HELVETICA;
    private static final float TITLE_SIZE = 24;
    private static final float SECTION_SIZE = 16;
    private static final float TEXT_SIZE = 12;
    private static final float LINE_SPACING = 1.5f;

    @Value("${pdf.storage.local.path}")
    private String localStoragePath;

    // Classe pour stocker l'état de la génération PDF
    private static class PdfState {
        PDDocument document;
        PDPageContentStream contentStream;
        PDPage currentPage;
        float yPosition;
    }

    public PdfMetadata generateAndSavePdf(Recipe recipe) throws IOException {
        // Préparer le dossier de stockage
        Path localPath = Paths.get(localStoragePath);
        if (!Files.exists(localPath)) {
            Files.createDirectories(localPath);
        }

        // Préparer le fichier PDF
        String fileName = "recipe_" + recipe.getId() + ".pdf";
        String filePath = localStoragePath + File.separator + fileName;

        // Créer l'état initial du PDF
        PdfState state = new PdfState();
        state.document = new PDDocument();
        state.currentPage = new PDPage(PDRectangle.A4);
        state.document.addPage(state.currentPage);
        state.contentStream = new PDPageContentStream(state.document, state.currentPage);
        state.yPosition = TOP_MARGIN;

        try {
            // Traiter le titre
            String title = recipe.getTitle();
            // Dessiner le titre
            drawTitle(state, title);
            state.yPosition -= 20; // Espace après le titre

            // Ajouter l'image si présente
            if (recipe.getImageUrl() != null && !recipe.getImageUrl().isEmpty()) {
                try {
                    String imagePath = storageService.downloadImage(recipe.getImageUrl(), localStoragePath);
                    File imgFile = new File(imagePath);

                    if (imgFile.exists() && imgFile.length() > 0) {
                        drawImage(state, imgFile);
                        Files.deleteIfExists(Paths.get(imagePath)); // Supprimer fichier temporaire
                    }
                } catch (Exception e) {
                    System.err.println("Warning: Failed to process image: " + e.getMessage());
                }
            }

            // Extraire les textes
            String description = extractSectionText(recipe.getDescription(), "Description:", "DESCRIPTION");
            String ingredients = extractSectionText(recipe.getIngredients(), "INGREDIENTS", null);
            String instructions = extractSectionText(recipe.getInstructions(), "Instructions:", "INSTRUCTIONS");

            // Ajouter les sections au PDF
            drawSection(state, "Description", description);
            drawSection(state, "Ingrédients", ingredients);
            drawSection(state, "Instructions", instructions);

            // Fermer le dernier contentStream
            state.contentStream.close();

            // Sauvegarder le PDF
            state.document.save(filePath);
            state.document.close();

            // Enregistrer les métadonnées
            File pdfFile = new File(filePath);

            // AMÉLIORATION : Préparation des tags personnalisés avec vérification du titre
            Map<String, String> customTags = new HashMap<>();

            // Vérifier et nettoyer le titre de la recette
            String recipeTitle = recipe.getTitle();
            if (recipeTitle == null || recipeTitle.trim().isEmpty()) {
                System.err.println("⚠️ ATTENTION: Le titre de la recette est vide ou null! ID: " + recipe.getId());
                recipeTitle = "Recette #" + recipe.getId(); // Fallback
            } else {
                recipeTitle = recipeTitle.trim();
                // Limiter la longueur si nécessaire (les APIs ont souvent des limites)
                if (recipeTitle.length() > 100) {
                    recipeTitle = recipeTitle.substring(0, 97) + "...";
                    System.out.println("📏 Titre de recette tronqué pour respecter les limites");
                }
            }

            customTags.put("tag2", recipeTitle);

            // Ajouter des informations supplémentaires pour le debug
            customTags.put("tag1", "recipe"); // Type de fichier
            customTags.put("tag3", "recipe-id-" + recipe.getId()); // ID pour référence

            System.out.println("📤 Upload du PDF avec les tags suivants:");
            System.out.println("  - tag1 (type): " + customTags.get("tag1"));
            System.out.println("  - tag2 (titre): " + customTags.get("tag2"));
            System.out.println("  - tag3 (ref): " + customTags.get("tag3"));

            String uploadResult = storageService.uploadFile(pdfFile, "application/pdf", customTags);

            // Extraire l'URL et l'ID interne du serveur
            String s3Url;
            String internalServerId = null;

            if (uploadResult.contains("||")) {
                String[] parts = uploadResult.split("\\|\\|");
                s3Url = parts[0];
                internalServerId = parts.length > 1 ? parts[1] : null;
                System.out.println("🔑 ID interne du serveur extrait: " + internalServerId);
            } else {
                s3Url = uploadResult;
            }

            recipe.setPdfUrl(s3Url);

            // Stocker l'ID interne du serveur pour la suppression
            if (internalServerId != null && !internalServerId.isEmpty()) {
                recipe.setExternalId(internalServerId);
                System.out.println("💾 ID interne du serveur stocké dans la recette: " + internalServerId);
            } else if (s3Url.contains("/student-bucket/") && s3Url.contains("recipe_" + recipe.getId())) {
                // Fallback sur l'ancien mécanisme
                String fileName2 = s3Url.substring(s3Url.lastIndexOf('/') + 1);
                if (fileName2.contains("-recipe_")) {
                    String uuid = fileName2.split("-recipe_")[0];
                    recipe.setExternalId(uuid);
                    System.out.println("💾 UUID externe stocké dans la recette: " + uuid);
                } else {
                    recipe.setExternalId(String.valueOf(recipe.getId()));
                    System.out.println("💾 ID externe stocké dans la recette: " + recipe.getId());
                }
            }

            PdfMetadata metadata = new PdfMetadata();
            metadata.setFileName(fileName);
            metadata.setContentType("application/pdf");
            metadata.setFileSize(pdfFile.length());
            metadata.setS3Url(s3Url);
            metadata.setLocalPath(filePath);
            metadata.setRecipe(recipe);

            return pdfMetadataRepository.save(metadata);
        } catch (Exception e) {
            // Assurer que les ressources sont fermées en cas d'erreur
            try {
                if (state.contentStream != null) {
                    state.contentStream.close();
                }
                if (state.document != null) {
                    state.document.close();
                }
            } catch (IOException ioe) {
                // Ignorer les erreurs de fermeture
            }
            throw e;
        }
    }

    private String extractSectionText(String text, String... prefixes) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        // Retirer les préfixes spécifiés
        for (String prefix : prefixes) {
            if (prefix != null && text.startsWith(prefix)) {
                text = text.substring(prefix.length()).trim();
            }
        }

        // Nettoyer le texte - supprimer les ":" isolés au début
        if (text.startsWith(":")) {
            text = text.substring(1).trim();
        }

        // Gérer le cas où le texte commence par des sauts de ligne suivis de ":"
        String[] lines = text.split("\n", 2);
        if (lines.length > 1 && lines[0].trim().equals(":")) {
            text = lines[1].trim();
        }

        return text;
    }

    // Vérifier l'espace disponible et créer une nouvelle page si nécessaire
    private void checkPageBreak(PdfState state, float neededSpace) throws IOException {
        if (state.yPosition - neededSpace < BOTTOM_MARGIN) {
            // Fermer la page actuelle
            state.contentStream.close();

            // Créer une nouvelle page
            state.currentPage = new PDPage(PDRectangle.A4);
            state.document.addPage(state.currentPage);
            state.contentStream = new PDPageContentStream(state.document, state.currentPage);
            state.yPosition = TOP_MARGIN;
        }
    }

    // Dessiner le titre
    private void drawTitle(PdfState state, String title) throws IOException {
        float titleWidth = TITLE_FONT.getStringWidth(title) / 1000 * TITLE_SIZE;

        // Si le titre est trop long, réduire la taille
        float fontSize = TITLE_SIZE;
        if (titleWidth > TEXT_WIDTH) {
            fontSize = TITLE_SIZE * TEXT_WIDTH / titleWidth;
        }

        // Vérifier l'espace disponible
        checkPageBreak(state, fontSize * LINE_SPACING);

        state.contentStream.beginText();
        state.contentStream.setFont(TITLE_FONT, fontSize);
        state.contentStream.newLineAtOffset(MARGIN, state.yPosition);
        state.contentStream.showText(title);
        state.contentStream.endText();

        state.yPosition -= (fontSize * LINE_SPACING);
    }

    // Dessiner une image
    private void drawImage(PdfState state, File imgFile) throws IOException {
        PDImageXObject image = PDImageXObject.createFromFileByContent(imgFile, state.document);

        // Calculer les dimensions de l'image proportionnellement
        float imageWidth = TEXT_WIDTH;
        float imageHeight = image.getHeight() * imageWidth / image.getWidth();

        // Vérifier l'espace disponible
        checkPageBreak(state, imageHeight);

        state.contentStream.drawImage(image, MARGIN, state.yPosition - imageHeight, imageWidth, imageHeight);

        state.yPosition -= (imageHeight + 20); // Espace après l'image
    }

    // Dessiner une section complète (titre + contenu)
    private void drawSection(PdfState state, String title, String content) throws IOException {
        // Vérifier l'espace pour le titre
        checkPageBreak(state, SECTION_SIZE * LINE_SPACING);

        // Dessiner le titre de section
        state.contentStream.beginText();
        state.contentStream.setFont(SECTION_FONT, SECTION_SIZE);
        state.contentStream.newLineAtOffset(MARGIN, state.yPosition);
        state.contentStream.showText(title);
        state.contentStream.endText();

        state.yPosition -= SECTION_SIZE * LINE_SPACING;

        // Dessiner le contenu
        drawMultilineText(state, content);
        state.yPosition -= 10; // Espace après la section
    }

    // Dessiner du texte multilignes avec gestion des sauts de page
    private void drawMultilineText(PdfState state, String text) throws IOException {
        if (text == null || text.isEmpty()) {
            state.yPosition -= (TEXT_SIZE * LINE_SPACING); // Espace si vide
            return;
        }

        // Nettoyer le texte
        text = text.replace("\r\n", "\n").replace("\r", "\n");

        // Diviser par lignes existantes
        String[] lines = text.split("\n");

        for (String line : lines) {
            // Couper les lignes trop longues
            float lineWidth = TEXT_FONT.getStringWidth(line) / 1000 * TEXT_SIZE;
            if (lineWidth <= TEXT_WIDTH) {
                // La ligne tient sur la largeur
                drawTextLine(state, line);
            } else {
                // Diviser en plusieurs lignes
                String[] words = line.split(" ");
                StringBuilder currentLine = new StringBuilder();

                for (String word : words) {
                    String testLine = currentLine.toString() + (currentLine.length() > 0 ? " " : "") + word;
                    float testWidth = TEXT_FONT.getStringWidth(testLine) / 1000 * TEXT_SIZE;

                    if (testWidth <= TEXT_WIDTH) {
                        // Ajouter le mot à la ligne actuelle
                        if (currentLine.length() > 0) {
                            currentLine.append(" ");
                        }
                        currentLine.append(word);
                    } else {
                        // Dessiner la ligne actuelle et commencer une nouvelle ligne
                        if (currentLine.length() > 0) {
                            drawTextLine(state, currentLine.toString());
                            currentLine = new StringBuilder(word);
                        } else {
                            // Le mot est plus long que la largeur disponible
                            drawTextLine(state, word);
                        }
                    }
                }

                // Dessiner la dernière ligne
                if (currentLine.length() > 0) {
                    drawTextLine(state, currentLine.toString());
                }
            }
        }
    }

    // Dessiner une seule ligne de texte, avec gestion des sauts de page
    private void drawTextLine(PdfState state, String line) throws IOException {
        // Vérifier l'espace disponible
        checkPageBreak(state, TEXT_SIZE);

        // Dessiner la ligne
        state.contentStream.beginText();
        state.contentStream.setFont(TEXT_FONT, TEXT_SIZE);
        state.contentStream.newLineAtOffset(MARGIN, state.yPosition);
        state.contentStream.showText(line);
        state.contentStream.endText();

        state.yPosition -= (TEXT_SIZE * LINE_SPACING);
    }
}