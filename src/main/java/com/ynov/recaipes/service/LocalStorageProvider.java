package com.ynov.recaipes.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Component
public class LocalStorageProvider implements StorageProvider {

    @Value("${pdf.storage.local.path:./pdfs}")
    private String localStoragePath;

    @Override
    public String uploadFile(File file, String contentType) {
        try {
            Path destinationDir = Paths.get(localStoragePath);
            if (!Files.exists(destinationDir)) {
                Files.createDirectories(destinationDir);
            }

            String uniqueFileName = UUID.randomUUID() + "-" + file.getName();
            Path destinationFile = destinationDir.resolve(uniqueFileName);

            Files.copy(file.toPath(), destinationFile, StandardCopyOption.REPLACE_EXISTING);

            return "file://" + destinationFile.toAbsolutePath();
        } catch (IOException e) {
            throw new RuntimeException("Failed to store file locally", e);
        }
    }

    @Override
    public String getFileUrl(String fileName) {
        return "file://" + Paths.get(localStoragePath).resolve(fileName).toAbsolutePath();
    }

    @Override
    public boolean isAvailable() {
        return true; // Toujours disponible
    }
}