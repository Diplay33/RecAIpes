package com.ynov.recaipes.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.core.io.FileSystemResource;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class ExternalBucketProvider implements StorageProvider {

    @Value("${external.bucket.enabled:true}")
    private boolean bucketEnabled;

    @Value("${external.bucket.url:http://141.94.115.201}")
    private String bucketBaseUrl;

    @Value("${external.bucket.group.id:8}")
    private Integer groupId;  // CHANGÉ : Integer au lieu de String

    @Value("${external.bucket.token:}")
    private String studentToken;  // NOUVEAU : Token d'authentification

    private final RestTemplate restTemplate;

    public ExternalBucketProvider(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public String uploadFile(File file, String contentType) {
        if (!isAvailable()) {
            throw new IllegalStateException("External Bucket Provider is not available");
        }

        if (studentToken == null || studentToken.isEmpty()) {
            throw new IllegalStateException("Student token required for upload");
        }

        try {
            String uploadUrl = bucketBaseUrl + "/student/upload";

            // Préparer les headers avec authentification Bearer
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            headers.setBearerAuth(studentToken);  // NOUVEAU : Bearer token

            // Préparer le body multipart selon le format Bruno
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", new FileSystemResource(file));
            body.add("idExterne", generateExternalId());

            // Tags spécifiques aux recettes
            body.add("tag1", "recipe"); // Type d'objet
            body.add("tag2", extractRecipeType(file.getName())); // Type de recette
            body.add("tag3", getCurrentDate()); // Date de création

            HttpEntity<MultiValueMap<String, Object>> requestEntity =
                    new HttpEntity<>(body, headers);

            // Envoyer la requête
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    uploadUrl, requestEntity, Map.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                System.out.println("Upload réussi vers bucket externe: " + responseBody);

                // Construire l'URL publique basée sur la réponse
                return constructPublicUrl(responseBody);
            } else {
                throw new RuntimeException("Failed to upload to external bucket: " + response.getStatusCode());
            }

        } catch (Exception e) {
            System.err.println("External bucket upload failed: " + e.getMessage());
            throw new RuntimeException("External bucket upload failed: " + e.getMessage(), e);
        }
    }

    @Override
    public String getFileUrl(String fileName) {
        // Pour le bucket externe, on utilise l'URL publique directe
        return bucketBaseUrl + "/public/file/" + fileName;
    }

    @Override
    public boolean isAvailable() {
        // Configuration requise : URL + groupID
        // Token optionnel (requis seulement pour upload)
        boolean available = bucketEnabled &&
                bucketBaseUrl != null && !bucketBaseUrl.isEmpty() &&
                groupId != null;

        if (available) {
            System.out.println("External Bucket Provider configuré pour: " + bucketBaseUrl +
                    " (Group ID: " + groupId + ")");
            if (studentToken == null || studentToken.isEmpty()) {
                System.out.println("⚠️  Warning: Pas de token configuré - Upload impossible");
            }
        } else {
            System.out.println("External Bucket Provider non disponible - configuration manquante");
        }

        return available;
    }

    /**
     * Recherche publique selon l'API Bruno (GET avec JSON body)
     */
    public Map<String, Object> searchFiles(String tag1, String tag2, String tag3) {
        try {
            String searchUrl = bucketBaseUrl + "/public/search";

            // Headers pour requête JSON
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            // Body JSON selon format Bruno
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("groupID", groupId);  // CORRIGÉ : Integer groupID

            // Ajouter les tags seulement s'ils ne sont pas vides
            if (tag1 != null && !tag1.isEmpty()) requestBody.put("tag1", tag1);
            if (tag2 != null && !tag2.isEmpty()) requestBody.put("tag2", tag2);
            if (tag3 != null && !tag3.isEmpty()) requestBody.put("tag3", tag3);

            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);

            System.out.println("Recherche dans bucket externe avec body JSON: " + requestBody);

            // CHANGÉ : GET au lieu de POST selon Bruno
            ResponseEntity<Map> response = restTemplate.exchange(
                    searchUrl, HttpMethod.GET, requestEntity, Map.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                System.out.println("Recherche réussie: " + response.getBody());
                return response.getBody();
            } else {
                System.err.println("Recherche échouée: " + response.getStatusCode());
                return Map.of("error", "Search failed with status: " + response.getStatusCode());
            }

        } catch (Exception e) {
            System.err.println("Search failed: " + e.getMessage());
            return Map.of("error", "Search failed: " + e.getMessage());
        }
    }

    /**
     * Recherche privée avec authentification
     */
    public Map<String, Object> searchFilesPrivate(String tag1, String tag2, String tag3) {
        if (studentToken == null || studentToken.isEmpty()) {
            return Map.of("error", "Token required for private search");
        }

        try {
            String searchUrl = bucketBaseUrl + "/student/upload/search";

            // Headers avec Bearer token
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(studentToken);

            // Body JSON vide + paramètres multipart selon Bruno
            Map<String, Object> requestBody = new HashMap<>();

            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                    searchUrl, HttpMethod.GET, requestEntity, Map.class);

            return response.getStatusCode().is2xxSuccessful() ?
                    response.getBody() :
                    Map.of("error", "Private search failed: " + response.getStatusCode());

        } catch (Exception e) {
            return Map.of("error", "Private search failed: " + e.getMessage());
        }
    }

    /**
     * Test de connectivité amélioré
     */
    public boolean testConnectivity() {
        try {
            // Test avec recherche publique (ne nécessite pas de token)
            Map<String, Object> result = searchFiles(null, null, null);
            return !result.containsKey("error");
        } catch (Exception e) {
            System.err.println("Connectivity test failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Info complète sur la configuration
     */
    public Map<String, Object> getProviderInfo() {
        Map<String, Object> info = new HashMap<>();
        info.put("baseUrl", bucketBaseUrl);
        info.put("groupId", groupId);
        info.put("hasToken", studentToken != null && !studentToken.isEmpty());
        info.put("canUpload", isAvailable() && studentToken != null && !studentToken.isEmpty());
        info.put("canSearchPublic", isAvailable());
        info.put("canSearchPrivate", isAvailable() && studentToken != null && !studentToken.isEmpty());
        return info;
    }

    private String generateExternalId() {
        return "recipe-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private String extractRecipeType(String fileName) {
        // Extraire le type de recette depuis le nom du fichier
        if (fileName.contains("dessert")) return "dessert";
        if (fileName.contains("plat")) return "main-course";
        if (fileName.contains("entree")) return "starter";
        return "general";
    }

    private String getCurrentDate() {
        return java.time.LocalDate.now().toString();
    }

    private String constructPublicUrl(Map<String, Object> responseBody) {
        // Construire l'URL publique basée sur la réponse du bucket
        if (responseBody.containsKey("fileUrl")) {
            return (String) responseBody.get("fileUrl");
        }
        if (responseBody.containsKey("fileName")) {
            return bucketBaseUrl + "/public/file/" + responseBody.get("fileName");
        }
        if (responseBody.containsKey("id")) {
            return bucketBaseUrl + "/public/file/" + responseBody.get("id");
        }

        // Fallback générique
        return bucketBaseUrl + "/public/file/unknown";
    }
}