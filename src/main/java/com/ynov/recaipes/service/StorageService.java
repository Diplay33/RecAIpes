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

        String providers = storageProviders.stream()
                .map(p -> p.getClass().getSimpleName() + " (available: " + p.isAvailable() + ")")
                .collect(Collectors.joining(", "));
        System.out.println("Available storage providers: " + providers);
    }

    public String uploadFile(File file, String contentType) {
        return uploadFile(file, contentType, null);
    }

    public String uploadFile(File file, String contentType, Map<String, String> customTags) {
        if (storageProviders.isEmpty()) {
            throw new IllegalStateException("No storage providers available");
        }

        List<StorageProvider> orderedProviders = new ArrayList<>();

        // 1. Bucket externe en priorité
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

        StorageProvider selectedProvider = orderedProviders.get(0);
        System.out.println("Using storage provider: " + selectedProvider.getClass().getSimpleName());

        try {
            String result = customTags != null ?
                    selectedProvider.uploadFile(file, contentType, customTags) :
                    selectedProvider.uploadFile(file, contentType);
            return result;
        } catch (Exception e) {
            System.err.println("Upload failed with " + selectedProvider.getClass().getSimpleName() +
                    ": " + e.getMessage());

            if (orderedProviders.size() > 1) {
                StorageProvider fallbackProvider = orderedProviders.get(1);
                System.out.println("Trying fallback provider: " + fallbackProvider.getClass().getSimpleName());
                String result = customTags != null ?
                        fallbackProvider.uploadFile(file, contentType, customTags) :
                        fallbackProvider.uploadFile(file, contentType);

                return result;
            }

            throw new RuntimeException("All storage providers failed", e);
        }
    }

    /**
     * Supprimer un fichier du stockage
     */
    public boolean deleteFile(String fileUrl) {
        if (fileUrl == null || fileUrl.isEmpty()) {
            System.err.println("❌ URL de fichier vide ou nulle");
            return false;
        }

        System.out.println("🗑️ Tentative de suppression du fichier: " + fileUrl);

        // Ignorer les URLs externes qui ne sont pas sur nos buckets
        if (isExternalNonDeletableUrl(fileUrl)) {
            System.out.println("⚠️ Fichier externe non supprimable ignoré: " + fileUrl);
            return true; // On considère comme "réussi" car on ne peut pas/ne veut pas le supprimer
        }

        // Identifier le type de stockage basé sur l'URL
        StorageProvider targetProvider = null;

        if (fileUrl.startsWith("file://")) {
            // Fichier local
            targetProvider = getLocalProvider();
        } else if (fileUrl.contains("141.94.115.201") || fileUrl.contains("/public/file/")) {
            // Bucket externe - vérifier que ce n'est pas "unknown"
            if (fileUrl.contains("/unknown")) {
                System.err.println("⚠️ Fichier avec ID 'unknown' - probablement pas uploadé correctement");
                return true; // Pas d'erreur, mais pas de suppression nécessaire
            }
            targetProvider = getExternalBucketProvider();
        }

        if (targetProvider != null && targetProvider.isAvailable()) {
            try {
                boolean success = targetProvider.deleteFile(fileUrl);
                if (success) {
                    System.out.println("✅ Fichier supprimé avec succès: " + fileUrl);
                } else {
                    System.err.println("❌ Échec de la suppression: " + fileUrl);
                }
                return success;
            } catch (Exception e) {
                System.err.println("❌ Erreur lors de la suppression: " + e.getMessage());
                return false;
            }
        } else {
            System.err.println("❌ Aucun provider disponible pour supprimer: " + fileUrl);
            return false;
        }
    }

    /**
     * Vérifie si l'URL est une URL externe qu'on ne peut/veut pas supprimer
     */
    public boolean isExternalNonDeletableUrl(String fileUrl) {
        if (fileUrl == null) {
            return false;
        }

        // URLs OpenAI DALL-E
        if (fileUrl.contains("oaidalleapiprodscus") ||
                fileUrl.contains("blob.core.windows.net") ||
                fileUrl.contains("openai")) {
            System.out.println("⚠️ URL DALL-E détectée comme non supprimable");
            return true;
        }

        // URLs d'autres services externes
        if (fileUrl.contains("amazonaws.com") ||
                fileUrl.contains("cloudfront.net") ||
                fileUrl.contains("googleapis.com")) {
            System.out.println("⚠️ URL externe détectée comme non supprimable");
            return true;
        }

        return false;
    }

    /**
     * Supprimer plusieurs fichiers
     */
    public Map<String, Boolean> deleteFiles(List<String> fileUrls) {
        Map<String, Boolean> results = new HashMap<>();

        if (fileUrls == null || fileUrls.isEmpty()) {
            return results;
        }

        // Déduplication des URLs
        Set<String> uniqueUrls = new HashSet<>(fileUrls);
        System.out.println("🗑️ Suppression de " + uniqueUrls.size() + " fichier(s) unique(s) du stockage...");

        for (String fileUrl : uniqueUrls) {
            if (fileUrl != null && !fileUrl.isEmpty()) {
                results.put(fileUrl, deleteFile(fileUrl));
            }
        }

        // Logs des résultats
        long successCount = results.values().stream().filter(success -> success).count();
        System.out.println("📊 Suppression terminée: " + successCount + "/" + uniqueUrls.size() + " fichiers supprimés");

        return results;
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

    public ExternalBucketProvider getExternalBucketProvider() {
        return storageProviders.stream()
                .filter(provider -> provider instanceof ExternalBucketProvider)
                .map(provider -> (ExternalBucketProvider) provider)
                .filter(ExternalBucketProvider::isAvailable)
                .findFirst()
                .orElse(null);
    }

    public LocalStorageProvider getLocalProvider() {
        return storageProviders.stream()
                .filter(provider -> provider instanceof LocalStorageProvider)
                .map(provider -> (LocalStorageProvider) provider)
                .filter(LocalStorageProvider::isAvailable)
                .findFirst()
                .orElse(null);
    }

    public boolean isExternalBucketAvailable() {
        return getExternalBucketProvider() != null;
    }

    public boolean isLocalStorageAvailable() {
        return getLocalProvider() != null;
    }

    public String getActiveStorageType() {
        if (isExternalBucketAvailable()) {
            return "External Bucket";
        } else if (isLocalStorageAvailable()) {
            return "Local";
        } else {
            return "None";
        }
    }

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

    public StorageProvider getAvailableProvider() {
        return storageProviders.stream()
                .filter(StorageProvider::isAvailable)
                .findFirst()
                .orElse(null);
    }

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