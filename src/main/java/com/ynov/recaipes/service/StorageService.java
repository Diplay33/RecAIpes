package com.ynov.recaipes.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class StorageService {

    private final List<StorageProvider> storageProviders;

    @Autowired
    public StorageService(List<StorageProvider> storageProviders) {
        this.storageProviders = storageProviders;

        // Afficher les providers disponibles
        String providers = storageProviders.stream()
                .map(p -> p.getClass().getSimpleName() + " (available: " + p.isAvailable() + ")")
                .collect(Collectors.joining(", "));
        System.out.println("Available storage providers: " + providers);
    }

    public String uploadFile(File file, String contentType) {
        // Vérifier si au moins un provider est disponible
        if (storageProviders.isEmpty()) {
            throw new IllegalStateException("No storage providers available");
        }

        // Ordre de priorité : ExternalBucket > Local
        List<StorageProvider> orderedProviders = new ArrayList<>();

        // 1. Bucket externe en priorité (fourni par l'école)
        storageProviders.stream()
                .filter(p -> p instanceof ExternalBucketProvider)
                .filter(StorageProvider::isAvailable)
                .findFirst()
                .ifPresent(orderedProviders::add);

        // 2. Stockage local en fallback
        storageProviders.stream()
                .filter(p -> p instanceof LocalStorageProvider)
                .filter(StorageProvider::isAvailable)
                .findFirst()
                .ifPresent(orderedProviders::add);

        if (orderedProviders.isEmpty()) {
            throw new IllegalStateException("No available storage providers");
        }

        // Utiliser le premier provider disponible dans l'ordre de priorité
        StorageProvider selectedProvider = orderedProviders.get(0);
        System.out.println("Using storage provider: " + selectedProvider.getClass().getSimpleName());

        try {
            return selectedProvider.uploadFile(file, contentType);
        } catch (Exception e) {
            System.err.println("Upload failed with " + selectedProvider.getClass().getSimpleName() +
                    ": " + e.getMessage());

            // Essayer le provider suivant si disponible
            if (orderedProviders.size() > 1) {
                StorageProvider fallbackProvider = orderedProviders.get(1);
                System.out.println("Trying fallback provider: " + fallbackProvider.getClass().getSimpleName());
                return fallbackProvider.uploadFile(file, contentType);
            }

            throw new RuntimeException("All storage providers failed", e);
        }
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

    /**
     * Obtient le provider de bucket externe s'il est disponible
     */
    public ExternalBucketProvider getExternalBucketProvider() {
        return storageProviders.stream()
                .filter(provider -> provider instanceof ExternalBucketProvider)
                .map(provider -> (ExternalBucketProvider) provider)
                .filter(ExternalBucketProvider::isAvailable)
                .findFirst()
                .orElse(null);
    }

    /**
     * Obtient le provider Local s'il est disponible
     */
    public LocalStorageProvider getLocalProvider() {
        return storageProviders.stream()
                .filter(provider -> provider instanceof LocalStorageProvider)
                .map(provider -> (LocalStorageProvider) provider)
                .filter(LocalStorageProvider::isAvailable)
                .findFirst()
                .orElse(null);
    }

    /**
     * Vérifie si le bucket externe est disponible
     */
    public boolean isExternalBucketAvailable() {
        return getExternalBucketProvider() != null;
    }

    /**
     * Vérifie si le stockage local est disponible
     */
    public boolean isLocalStorageAvailable() {
        return getLocalProvider() != null;
    }

    /**
     * Retourne le type de stockage actuellement utilisé
     */
    public String getActiveStorageType() {
        if (isExternalBucketAvailable()) {
            return "External Bucket";
        } else if (isLocalStorageAvailable()) {
            return "Local";
        } else {
            return "None";
        }
    }

    /**
     * Test de connectivité pour tous les providers
     */
    public Map<String, Object> getDetailedStorageInfo() {
        Map<String, Object> info = new HashMap<>();

        ExternalBucketProvider externalProvider = getExternalBucketProvider();
        if (externalProvider != null) {
            info.put("externalBucket", Map.of(
                    "available", true,
                    "connectivity", externalProvider.testConnectivity()
            ));
        } else {
            info.put("externalBucket", Map.of("available", false));
        }

        info.put("localStorage", isLocalStorageAvailable());
        info.put("activeType", getActiveStorageType());

        return info;
    }

    /**
     * Obtient n'importe quel provider disponible
     */
    public StorageProvider getAvailableProvider() {
        return storageProviders.stream()
                .filter(StorageProvider::isAvailable)
                .findFirst()
                .orElse(null);
    }

    /**
     * Liste tous les providers avec leur statut
     */
    public List<Map<String, Object>> getAllProvidersStatus() {
        return storageProviders.stream()
                .map(provider -> {
                    Map<String, Object> status = new HashMap<>();
                    status.put("type", provider.getClass().getSimpleName());
                    status.put("available", provider.isAvailable());
                    return status;
                })
                .collect(Collectors.toList());
    }
}