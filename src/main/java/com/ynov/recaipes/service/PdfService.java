package com.ynov.recaipes.service;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.io.image.ImageDataFactory;
import com.ynov.recaipes.model.Recipe;
import com.ynov.recaipes.model.PdfMetadata;
import com.ynov.recaipes.repository.PdfMetadataRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
@RequiredArgsConstructor
public class PdfService {
    private final PdfMetadataRepository pdfMetadataRepository;
    private final StorageService storageService;
    
    @Value("${pdf.storage.local.path}")
    private String localStoragePath;

    public PdfMetadata generateAndSavePdf(Recipe recipe) throws IOException {
        Path localPath = Paths.get(localStoragePath);
        if (!Files.exists(localPath)) {
            Files.createDirectories(localPath);
        }
        
        String fileName = "recipe_" + recipe.getId() + ".pdf";
        String filePath = localStoragePath + File.separator + fileName;
        
        PdfWriter writer = new PdfWriter(filePath);
        PdfDocument pdf = new PdfDocument(writer);
        Document document = new Document(pdf);
        
        document.add(new Paragraph(recipe.getTitle()).setBold().setFontSize(24));
        
        if (recipe.getImageUrl() != null && !recipe.getImageUrl().isEmpty()) {
            Image image = new Image(ImageDataFactory.create(new URL(recipe.getImageUrl())));
            image.setWidth(400);
            document.add(image);
        }
        
        document.add(new Paragraph("Description:").setBold().setFontSize(16));
        document.add(new Paragraph(recipe.getDescription()));
        document.add(new Paragraph("\n"));
        
        document.add(new Paragraph("Ingredients:").setBold().setFontSize(16));
        document.add(new Paragraph(recipe.getIngredients()));
        document.add(new Paragraph("\n"));
        
        document.add(new Paragraph("Instructions:").setBold().setFontSize(16));
        document.add(new Paragraph(recipe.getInstructions()));
        
        document.close();
        
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
}