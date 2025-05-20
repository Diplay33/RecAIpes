package com.ynov.recaipes.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import com.ynov.recaipes.model.Recipe;
import com.ynov.recaipes.model.PdfMetadata;
import com.ynov.recaipes.repository.PdfMetadataRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PdfService {
    private final PdfMetadataRepository pdfMetadataRepository;
    private final StorageService storageService;

    @Value("${pdf.storage.local.path}")
    private String localStoragePath;

    // Creates a PDF document from recipe data, saves it locally and to S3,
    // then records the metadata in the database
    public PdfMetadata generateAndSavePdf(Recipe recipe) throws IOException {
        // Ensure storage directory exists
        Path localPath = Paths.get(localStoragePath);
        if (!Files.exists(localPath)) {
            Files.createDirectories(localPath);
        }

        // Create PDF with recipe content
        String fileName = "recipe_" + recipe.getId() + ".pdf";
        String filePath = localStoragePath + File.separator + fileName;

        PDDocument document = new PDDocument();
        PDPage page = new PDPage(PDRectangle.A4);
        document.addPage(page);

        PDPageContentStream contentStream = new PDPageContentStream(document, page);

        // Add title
        contentStream.beginText();
        contentStream.setFont(PDType1Font.HELVETICA_BOLD, 24);
        contentStream.newLineAtOffset(50, 770);
        contentStream.showText(recipe.getTitle());
        contentStream.endText();

        // Image handling would require downloading the image first
        if (recipe.getImageUrl() != null && !recipe.getImageUrl().isEmpty()) {
            String imagePath = storageService.downloadImage(recipe.getImageUrl(), localStoragePath);
            PDImageXObject image = PDImageXObject.createFromFile(imagePath, document);
            contentStream.drawImage(image, 50, 600, 400, 300);

            // Delete temporary image file
            Files.deleteIfExists(Paths.get(imagePath));
        }

        // Text writing helper function
        List<String> textLines = new ArrayList<>();
        textLines.add("Description: ");
        addTextWithLineBreaks(recipe.getDescription(), textLines);
        textLines.add("\nIngredients: ");
        addTextWithLineBreaks(recipe.getIngredients(), textLines);
        textLines.add("\nInstructions: ");
        addTextWithLineBreaks(recipe.getInstructions(), textLines);

        float y = 580; // Starting position
        if (recipe.getImageUrl() != null && !recipe.getImageUrl().isEmpty()) {
            y = 280; // Lower position if image exists
        }

        for (String line : textLines) {
            if (line.startsWith("Description: ") || line.startsWith("Ingredients: ") || line.startsWith("Instructions: ")) {
                contentStream.beginText();
                contentStream.setFont(PDType1Font.HELVETICA_BOLD, 16);
                contentStream.newLineAtOffset(50, y);
                contentStream.showText(line);
                contentStream.endText();
            } else {
                contentStream.beginText();
                contentStream.setFont(PDType1Font.HELVETICA, 12);
                contentStream.newLineAtOffset(50, y);
                contentStream.showText(line);
                contentStream.endText();
            }
            y -= 15; // Move down for next line

            // Create new page if needed
            if (y < 50) {
                contentStream.close();
                PDPage newPage = new PDPage(PDRectangle.A4);
                document.addPage(newPage);
                contentStream = new PDPageContentStream(document, newPage);
                y = 780;
            }
        }

        contentStream.close();
        document.save(filePath);
        document.close();

        // Upload to S3 and save metadata
        File pdfFile = new File(filePath);
        String s3Url = storageService.uploadFile(pdfFile, "application/pdf");

        recipe.setPdfUrl(s3Url);

        PdfMetadata metadata = new PdfMetadata();
        metadata.setFileName(fileName);
        metadata.setContentType("application/pdf");
        metadata.setFileSize(pdfFile.length());
        metadata.setS3Url(s3Url);
        metadata.setLocalPath(filePath);
        metadata.setRecipe(recipe);

        return pdfMetadataRepository.save(metadata);
    }

    // Helper method to handle line breaking
    private void addTextWithLineBreaks(String text, List<String> lines) {
        if (text == null || text.isEmpty()) {
            lines.add("");
            return;
        }

        int maxCharsPerLine = 80;

        for (int i = 0; i < text.length(); i += maxCharsPerLine) {
            int endIndex = Math.min(i + maxCharsPerLine, text.length());
            if (endIndex < text.length()) {
                // Try to find space for better line breaks
                int spaceIndex = text.lastIndexOf(' ', endIndex);
                if (spaceIndex > i && spaceIndex - i > maxCharsPerLine / 2) {
                    endIndex = spaceIndex;
                }
            }
            lines.add(text.substring(i, endIndex));
        }
    }
}