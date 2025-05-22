package com.ynov.recaipes.controller;

import com.ynov.recaipes.service.ExternalBucketProvider;
import com.ynov.recaipes.service.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/bucket")
@RequiredArgsConstructor
public class BucketTestController {

    private final StorageService storageService;

    /**
     * Informations détaillées sur tous les providers de stockage
     */
    @GetMapping("/info")
    public ResponseEntity<Map<String, Object>> getStorageInfo() {
        Map<String, Object> info = new HashMap<>();

        // Info générale - Méthode simplifiée pour éviter l'erreur
        String activeType = "Unknown";
        if (storageService.isExternalBucketAvailable()) {
            activeType = "External Bucket";
        } else if (storageService.isLocalStorageAvailable()) {
            activeType = "Local";
        }
        info.put("activeStorageType", activeType);

        // Bucket externe
        ExternalBucketProvider externalProvider = storageService.getExternalBucketProvider();
        if (externalProvider != null) {
            info.put("externalBucket", Map.of(
                    "available", true,
                    "configured", true
            ));
        } else {
            info.put("externalBucket", Map.of(
                    "available", false,
                    "reason", "Not configured or disabled"
            ));
        }

        // Local
        info.put("localStorage", Map.of(
                "available", storageService.isLocalStorageAvailable()
        ));

        return ResponseEntity.ok(info);
    }

    /**
     * Test du token étudiant
     */
    @GetMapping("/test-token")
    public ResponseEntity<Map<String, Object>> testStudentToken() {
        ExternalBucketProvider provider = storageService.getExternalBucketProvider();

        if (provider == null) {
            return ResponseEntity.ok(Map.of(
                    "success", false,
                    "message", "External bucket provider not configured"
            ));
        }

        Map<String, Object> providerInfo = provider.getProviderInfo();

        return ResponseEntity.ok(Map.of(
                "success", true,
                "providerInfo", providerInfo,
                "tokenPresent", providerInfo.get("hasToken"),
                "canUpload", providerInfo.get("canUpload")
        ));
    }

    /**
     * Recherche dans le bucket externe (publique)
     */
    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> searchInExternalBucket(
            @RequestParam(required = false) String tag1,
            @RequestParam(required = false) String tag2,
            @RequestParam(required = false) String tag3) {

        ExternalBucketProvider provider = storageService.getExternalBucketProvider();

        if (provider == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", "External bucket provider not available"
            ));
        }

        try {
            Map<String, Object> results = provider.searchFiles(tag1, tag2, tag3);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "results", results,
                    "queryParams", Map.of(
                            "tag1", tag1 != null ? tag1 : "null",
                            "tag2", tag2 != null ? tag2 : "null",
                            "tag3", tag3 != null ? tag3 : "null"
                    )
            ));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of(
                    "success", false,
                    "error", "Search failed: " + e.getMessage()
            ));
        }
    }

    /**
     * Recherche privée dans le bucket externe (avec token)
     */
    @GetMapping("/search-private")
    public ResponseEntity<Map<String, Object>> searchPrivateInExternalBucket(
            @RequestParam(required = false) String tag1,
            @RequestParam(required = false) String tag2,
            @RequestParam(required = false) String tag3) {

        ExternalBucketProvider provider = storageService.getExternalBucketProvider();

        if (provider == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", "External bucket provider not available"
            ));
        }

        try {
            Map<String, Object> results = provider.searchFilesPrivate(tag1, tag2, tag3);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "results", results,
                    "searchType", "private"
            ));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of(
                    "success", false,
                    "error", "Private search failed: " + e.getMessage()
            ));
        }
    }

    /**
     * Informations détaillées sur le provider externe
     */
    @GetMapping("/external-info")
    public ResponseEntity<Map<String, Object>> getExternalProviderInfo() {
        ExternalBucketProvider provider = storageService.getExternalBucketProvider();

        if (provider == null) {
            return ResponseEntity.ok(Map.of(
                    "available", false,
                    "reason", "Provider not configured"
            ));
        }

        return ResponseEntity.ok(provider.getProviderInfo());
    }
}