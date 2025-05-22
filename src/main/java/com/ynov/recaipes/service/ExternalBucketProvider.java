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
    private Integer groupId;

    @Value("${external.bucket.token:}")
    private String studentToken;

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

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(studentToken);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", new FileSystemResource(file));
            body.add("idExterne", generateExternalIdNumeric());

            body.add("tag1", "recipe");
            body.add("tag2", extractRecipeType(file.getName()));
            body.add("tag3", getCurrentDate());

            HttpEntity<MultiValueMap<String, Object>> requestEntity =
                    new HttpEntity<>(body, headers);

            System.out.println("🚀 Upload vers bucket externe: " + uploadUrl);
            System.out.println("📦 Token: " + (studentToken != null ? "✅ Présent" : "❌ Manquant"));
            System.out.println("📎 Fichier: " + file.getName() + " (" + file.length() + " bytes)");

            ResponseEntity<Map> response = restTemplate.postForEntity(
                    uploadUrl, requestEntity, Map.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                System.out.println("✅ Upload réussi vers bucket externe: " + responseBody);

                return constructPublicUrl(responseBody);
            } else {
                throw new RuntimeException("Failed to upload to external bucket: " + response.getStatusCode());
            }

        } catch (Exception e) {
            System.err.println("❌ External bucket upload failed: " + e.getMessage());
            throw new RuntimeException("External bucket upload failed: " + e.getMessage(), e);
        }
    }

    @Override
    public String getFileUrl(String fileName) {
        return bucketBaseUrl + "/public/file/" + fileName;
    }

    @Override
    public boolean isAvailable() {
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

    @Override
    public boolean deleteFile(String fileUrl) {
        if (studentToken == null || studentToken.isEmpty()) {
            System.err.println("❌ Token requis pour supprimer un fichier du bucket externe");
            return false;
        }

        try {
            // Extraire l'ID du fichier depuis l'URL
            String fileId = extractFileIdFromUrl(fileUrl);
            if (fileId == null) {
                System.err.println("❌ Impossible d'extraire l'ID du fichier depuis l'URL: " + fileUrl);
                return false;
            }

            String deleteUrl = bucketBaseUrl + "/student/upload/" + fileId;

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(studentToken);

            HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

            System.out.println("🗑️ Suppression du fichier: " + deleteUrl);

            ResponseEntity<Map> response = restTemplate.exchange(
                    deleteUrl, HttpMethod.DELETE, requestEntity, Map.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                System.out.println("✅ Fichier supprimé avec succès du bucket externe");
                return true;
            } else {
                System.err.println("❌ Échec de la suppression: " + response.getStatusCode());
                return false;
            }

        } catch (Exception e) {
            System.err.println("❌ Erreur lors de la suppression du fichier: " + e.getMessage());
            return false;
        }
    }

    /**
     * Extraire l'ID du fichier depuis l'URL
     */
    private String extractFileIdFromUrl(String fileUrl) {
        if (fileUrl == null || fileUrl.isEmpty()) {
            return null;
        }

        // Format attendu: http://141.94.115.201/public/file/{id}
        if (fileUrl.contains("/public/file/")) {
            String[] parts = fileUrl.split("/public/file/");
            if (parts.length > 1) {
                return parts[1];
            }
        }

        // Si l'URL ne correspond pas au format attendu, essayer d'extraire le dernier segment
        String[] segments = fileUrl.split("/");
        if (segments.length > 0) {
            return segments[segments.length - 1];
        }

        return null;
    }

    public Map<String, Object> searchFiles(String tag1, String tag2, String tag3) {
        try {
            String searchUrl = bucketBaseUrl + "/public/search";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("groupID", groupId);

            if (tag1 != null && !tag1.isEmpty()) requestBody.put("tag1", tag1);
            if (tag2 != null && !tag2.isEmpty()) requestBody.put("tag2", tag2);
            if (tag3 != null && !tag3.isEmpty()) requestBody.put("tag3", tag3);

            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);

            System.out.println("Recherche dans bucket externe avec body JSON: " + requestBody);

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

    public Map<String, Object> searchFilesPrivate(String tag1, String tag2, String tag3) {
        if (studentToken == null || studentToken.isEmpty()) {
            return Map.of("error", "Token required for private search");
        }

        try {
            String searchUrl = bucketBaseUrl + "/student/upload/search";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(studentToken);

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

    public boolean testConnectivity() {
        try {
            Map<String, Object> result = searchFiles(null, null, null);
            return !result.containsKey("error");
        } catch (Exception e) {
            System.err.println("Connectivity test failed: " + e.getMessage());
            return false;
        }
    }

    public Map<String, Object> getProviderInfo() {
        Map<String, Object> info = new HashMap<>();
        info.put("baseUrl", bucketBaseUrl);
        info.put("groupId", groupId);
        info.put("hasToken", studentToken != null && !studentToken.isEmpty());
        info.put("canUpload", isAvailable() && studentToken != null && !studentToken.isEmpty());
        info.put("canSearchPublic", isAvailable());
        info.put("canSearchPrivate", isAvailable() && studentToken != null && !studentToken.isEmpty());
        info.put("canDelete", isAvailable() && studentToken != null && !studentToken.isEmpty());
        return info;
    }

    private String generateExternalIdNumeric() {
        return String.valueOf(System.currentTimeMillis() % 100000);
    }

    private String extractRecipeType(String fileName) {
        if (fileName.contains("dessert")) return "dessert";
        if (fileName.contains("plat")) return "main-course";
        if (fileName.contains("entree")) return "starter";
        return "general";
    }

    private String getCurrentDate() {
        return java.time.LocalDate.now().toString();
    }

    private String constructPublicUrl(Map<String, Object> responseBody) {
        if (responseBody.containsKey("fileUrl")) {
            return (String) responseBody.get("fileUrl");
        }
        if (responseBody.containsKey("fileName")) {
            return bucketBaseUrl + "/public/file/" + responseBody.get("fileName");
        }
        if (responseBody.containsKey("id")) {
            return bucketBaseUrl + "/public/file/" + responseBody.get("id");
        }

        return bucketBaseUrl + "/public/file/unknown";
    }
}