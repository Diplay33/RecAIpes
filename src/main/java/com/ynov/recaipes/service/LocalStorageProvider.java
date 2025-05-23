package com.ynov.recaipes.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;

@Component
public class LocalStorageProvider implements StorageProvider {

    @Value("${pdf.storage.local.path:./pdfs}")
    private String localStoragePath;

    @Override
    public String uploadFile(File file, String contentType) {
        return uploadFile(file, contentType, null);
    }

    @Override
    public String uploadFile(File file, String contentType, Map<String, String> customTags) {
        try {
            Path destinationDir = Paths.get(localStoragePath);
            if (!Files.exists(destinationDir)) {
                Files.createDirectories(destinationDir);
            }

            String uniqueFileName = UUID.randomUUID() + "-" + file.getName();
            Path destinationFile = destinationDir.resolve(uniqueFileName);

            Files.copy(file.toPath(), destinationFile, StandardCopyOption.REPLACE_EXISTING);

            // Ajout de l'ID fictif pour la compatibilité avec le nouveau format
            return "file://" + destinationFile.toAbsolutePath() + "||local";
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

    @Override
    public boolean deleteFile(String fileUrl) {
        try {
            // Extraire le chemin du fichier depuis l'URL file://
            if (fileUrl.startsWith("file://")) {
                String filePath = fileUrl.substring(7); // Enlever "file://"
                if (filePath.contains("||")) {
                    filePath = filePath.split("\\|\\|")[0]; // Enlever l'ID fictif
                }
                Path path = Paths.get(filePath);

                if (Files.exists(path)) {
                    Files.delete(path);
                    System.out.println("✅ Fichier local supprimé: " + filePath);
                    return true;
                } else {
                    System.err.println("⚠️ Fichier local introuvable: " + filePath);
                    return false;
                }
            } else {
                System.err.println("❌ URL de fichier locale invalide: " + fileUrl);
                return false;
            }
        } catch (IOException e) {
            System.err.println("❌ Erreur lors de la suppression du fichier local: " + e.getMessage());
            return false;
        }
    }
}