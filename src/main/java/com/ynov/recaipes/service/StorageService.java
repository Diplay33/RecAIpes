package com.ynov.recaipes.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class StorageService {

    private final List<StorageProvider> storageProviders;

    @Autowired
    public StorageService(List<StorageProvider> storageProviders) {
        this.storageProviders = storageProviders;

        // Afficher les providers disponibles
        String providers = storageProviders.stream()
                .map(p -> p.getClass().getSimpleName())
                .collect(Collectors.joining(", "));
        System.out.println("Available storage providers: " + providers);
    }

    public String uploadFile(File file, String contentType) {
        // Vérifier si au moins un provider est disponible
        if (storageProviders.isEmpty()) {
            throw new IllegalStateException("No storage providers available");
        }

        // Utiliser le premier provider disponible
        for (StorageProvider provider : storageProviders) {
            if (provider.isAvailable()) {
                System.out.println("Using storage provider: " + provider.getClass().getSimpleName());
                return provider.uploadFile(file, contentType);
            }
        }

        throw new IllegalStateException("No available storage providers");
    }

    public String downloadImage(String imageUrl, String destinationDir) throws IOException {
        try {
            URL url = new URL(imageUrl);
            String fileName = UUID.randomUUID() + ".jpg";
            String filePath = destinationDir + File.separator + fileName;

            byte[] imageBytes = url.openStream().readAllBytes();
            Files.write(Paths.get(filePath), imageBytes);

            return filePath;
        } catch (Exception e) {
            throw new IOException("Failed to download image: " + e.getMessage(), e);
        }
    }
}