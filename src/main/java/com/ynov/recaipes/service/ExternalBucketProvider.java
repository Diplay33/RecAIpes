package com.ynov.recaipes.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.core.io.FileSystemResource;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class ExternalBucketProvider implements StorageProvider {

    @Value("${external.bucket.enabled:true}")
    private boolean bucketEnabled;

    @Value("${external.bucket.url:http://141.94.115.201}")
    private String bucketBaseUrl;

    // This value from properties is no longer used for the public search, as we hardcode '6'.
    @Value("${external.bucket.group.id:8}")
    private Integer groupId;

    @Value("${external.bucket.token:}")
    private String studentToken;

    private final RestTemplate restTemplate;

    public ExternalBucketProvider(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    // ... (uploadFile and other methods remain the same)
    @Override
    public String uploadFile(File file, String contentType) {
        return uploadFile(file, contentType, null);
    }

    @Override
    public String uploadFile(File file, String contentType, Map<String, String> customTags) {
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

            String customExternalId = generateExternalIdNumeric();
            body.add("idExterne", customExternalId);

            String recipeName = getRecipeName(customTags, file.getName());

            body.add("tag1", "recipe");
            body.add("tag2", recipeName);
            body.add("tag3", getCurrentDateWithTime());
            body.add("generateThumbnail", true);

            HttpEntity<MultiValueMap<String, Object>> requestEntity =
                    new HttpEntity<>(body, headers);

            System.out.println("🚀 Upload vers bucket externe: " + uploadUrl);
            System.out.println("📦 Token: " + (studentToken != null ? "✅ Présent" : "❌ Manquant"));
            System.out.println("📎 Fichier: " + file.getName() + " (" + file.length() + " bytes)");
            System.out.println("🏷️ Tags: tag1=recipe, tag2=" + recipeName + ", tag3=" + getCurrentDateWithTime());

            ResponseEntity<Map> response = restTemplate.postForEntity(
                    uploadUrl, requestEntity, Map.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                System.out.println("✅ Upload réussi vers bucket externe: " + responseBody);

                String serverId = null;
                if (responseBody.containsKey("idExterne")) {
                    serverId = String.valueOf(responseBody.get("idExterne"));
                    System.out.println("🔑 ID externe du serveur: " + serverId);
                }

                String publicUrl = constructPublicUrl(responseBody);
                return publicUrl + "||" + serverId;
            } else {
                throw new RuntimeException("Failed to upload to external bucket: " + response.getStatusCode());
            }
        } catch (Exception e) {
            System.err.println("❌ External bucket upload failed: " + e.getMessage());
            throw new RuntimeException("External bucket upload failed: " + e.getMessage(), e);
        }
    }

    /**
     * --- METHODE CORRIGÉE ---
     * Builds the URL with the group ID in the path and removes it from the body.
     */
    public Map<String, Object> searchFiles(String tag1, String tag2, String tag3) {
        try {
            // --- FIX ---
            // The group ID is now part of the URL path, as per your instruction.
            String searchUrl = bucketBaseUrl + "/public/search/6";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            // The request body no longer needs the groupID.
            Map<String, Object> requestBody = new HashMap<>();
            if (tag1 != null && !tag1.isEmpty()) requestBody.put("tag1", tag1);
            if (tag2 != null && !tag2.isEmpty()) requestBody.put("tag2", tag2);
            if (tag3 != null && !tag3.isEmpty()) requestBody.put("tag3", tag3);

            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);

            System.out.println("Recherche publique dans bucket sur l'URL: " + searchUrl);

            ResponseEntity<Map> response = restTemplate.exchange(
                    searchUrl, HttpMethod.GET, requestEntity, Map.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                System.out.println("Recherche publique réussie: " + response.getBody());
                return response.getBody();
            } else {
                System.err.println("Recherche publique échouée: " + response.getStatusCode());
                return Map.of("error", "Search failed with status: " + response.getStatusCode());
            }

        } catch (Exception e) {
            System.err.println("Search failed: " + e.getMessage());
            return Map.of("error", "Search failed: " + e.getMessage());
        }
    }

    // ... (le reste du fichier est inchangé)
    private String getRecipeName(Map<String, String> customTags, String fileName) {
        if (customTags != null && customTags.containsKey("tag2")) {
            String providedTitle = customTags.get("tag2");
            if (providedTitle != null && !providedTitle.trim().isEmpty()) {
                System.out.println("✅ Utilisation du titre fourni: " + providedTitle);
                return providedTitle.trim();
            } else {
                System.out.println("⚠️ Titre fourni vide ou null dans customTags");
            }
        } else {
            System.out.println("⚠️ Aucun customTags fourni ou pas de tag2");
        }
        String extractedName = extractRecipeNameFromFileName(fileName);
        if (extractedName == null || extractedName.trim().isEmpty()) {
            extractedName = "Recette du " + getCurrentDateWithTime().split(" ")[0];
        }
        System.out.println("⚠️ Utilisation du nom extrait/généré: " + extractedName);
        return extractedName;
    }
    private String extractRecipeNameFromFileName(String fileName) {
        if (fileName == null || fileName.trim().isEmpty()) {
            return "Recette Sans Nom";
        }
        if (fileName.contains("recipe_")) {
            try {
                String recipeId = fileName.split("recipe_")[1].replace(".pdf", "");
                return "Recette Générée #" + recipeId;
            } catch (Exception e) {
                System.err.println("Erreur lors de l'extraction de l'ID de recette: " + e.getMessage());
                return "Recette PDF";
            }
        }
        String nameWithoutExtension = fileName;
        if (fileName.contains(".")) {
            nameWithoutExtension = fileName.substring(0, fileName.lastIndexOf("."));
        }
        return formatRecipeName(nameWithoutExtension);
    }
    private String formatRecipeName(String rawName) {
        if (rawName == null || rawName.trim().isEmpty()) {
            return "Recette Sans Nom";
        }
        String formatted = rawName.replace("_", " ").replace("-", " ");
        String[] words = formatted.split("\\s+");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) {
                if (result.length() > 0) {
                    result.append(" ");
                }
                result.append(word.substring(0, 1).toUpperCase());
                if (word.length() > 1) {
                    result.append(word.substring(1).toLowerCase());
                }
            }
        }
        return result.toString();
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
            System.out.println("🚀 Tentative de suppression pour: " + fileUrl);
            String idFromUrl = null;
            String originalFileUrl = fileUrl;
            if (fileUrl.contains("||")) {
                String[] parts = fileUrl.split("\\|\\|");
                idFromUrl = parts.length > 1 ? parts[1] : null;
                fileUrl = parts[0];
            }
            if (idFromUrl != null) {
                System.out.println("🔑 Utilisation de l'ID stocké: " + idFromUrl);
                try {
                    String deleteUrl = bucketBaseUrl + "/student/upload/" + idFromUrl;
                    HttpHeaders headers = new HttpHeaders();
                    headers.setBearerAuth(studentToken);
                    HttpEntity<Void> requestEntity = new HttpEntity<>(headers);
                    ResponseEntity<Map> response = restTemplate.exchange(
                            deleteUrl, HttpMethod.DELETE, requestEntity, Map.class);
                    if (response.getStatusCode().is2xxSuccessful()) {
                        System.out.println("✅ Suppression réussie avec ID: " + idFromUrl);
                        try {
                            Thread.sleep(1000);
                            HttpHeaders verifyHeaders = new HttpHeaders();
                            HttpEntity<Void> verifyRequest = new HttpEntity<>(verifyHeaders);
                            restTemplate.exchange(fileUrl, HttpMethod.HEAD, verifyRequest, byte[].class);
                            System.err.println("⚠️ ATTENTION: Le fichier semble toujours accessible!");
                        } catch (Exception e) {
                            System.out.println("✅ Vérification: Le fichier n'est plus accessible");
                        }
                        return true;
                    }
                } catch (Exception e) {
                    if (e.getMessage() != null && e.getMessage().contains("Aucun élément avec l'ID")) {
                        System.out.println("✅ Le fichier a déjà été supprimé (ID: " + idFromUrl + ")");
                        return true;
                    }
                    System.out.println("⚠️ Échec avec ID " + idFromUrl + ": " + e.getMessage());
                }
            }
            List<String> idsToTry = new ArrayList<>();
            String extractedId = extractFileIdFromUrl(fileUrl);
            if (extractedId != null) {
                idsToTry.add(extractedId);
                System.out.println("📄 ID extrait du nom de fichier: " + extractedId);
            }
            for (int i = 0; i < 5; i++) {
                String numericId = String.valueOf(i + 1);
                if (!idsToTry.contains(numericId)) {
                    idsToTry.add(numericId);
                }
            }
            System.out.println("🔄 Tentative avec " + idsToTry.size() + " IDs possibles: " + idsToTry);
            for (String id : idsToTry) {
                try {
                    String deleteUrl = bucketBaseUrl + "/student/upload/" + id;
                    HttpHeaders headers = new HttpHeaders();
                    headers.setBearerAuth(studentToken);
                    HttpEntity<Void> requestEntity = new HttpEntity<>(headers);
                    System.out.println("🗑️ Tentative de suppression avec ID: " + id);
                    ResponseEntity<Map> response = restTemplate.exchange(
                            deleteUrl, HttpMethod.DELETE, requestEntity, Map.class);
                    if (response.getStatusCode().is2xxSuccessful()) {
                        System.out.println("✅ Suppression réussie avec ID: " + id);
                        return true;
                    }
                } catch (Exception e) {
                    System.out.println("⚠️ Échec avec ID " + id + ": " + e.getMessage());
                }
            }
            try {
                HttpHeaders verifyHeaders = new HttpHeaders();
                HttpEntity<Void> verifyRequest = new HttpEntity<>(verifyHeaders);
                ResponseEntity<byte[]> verifyResponse = restTemplate.exchange(
                        fileUrl, HttpMethod.HEAD, verifyRequest, byte[].class);
                if (verifyResponse.getStatusCode().is2xxSuccessful()) {
                    System.err.println("⚠️ ATTENTION: Le fichier est toujours accessible!");
                    return false;
                }
            } catch (Exception e) {
                System.out.println("✅ Le fichier n'est plus accessible");
                return true;
            }
            return false;
        } catch (Exception e) {
            System.err.println("❌ Erreur générale lors de la suppression: " + e.getMessage());
            return false;
        }
    }
    private String getCurrentDateWithTime() {
        java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        return java.time.LocalDateTime.now().format(formatter);
    }
    private String extractFileIdFromUrl(String fileUrl) {
        if (fileUrl == null || fileUrl.isEmpty()) {
            return null;
        }
        System.out.println("🔍 Extraction ID depuis URL: " + fileUrl);
        if (fileUrl.contains("/public/file/")) {
            String[] parts = fileUrl.split("/public/file/");
            if (parts.length > 1) {
                String id = parts[1];
                System.out.println("✅ ID extrait depuis /public/file/: " + id);
                return id;
            }
        }
        if (fileUrl.contains("/student-bucket/")) {
            String[] parts = fileUrl.split("/");
            if (parts.length > 0) {
                String fileName = parts[parts.length - 1];
                if (fileName.contains("-recipe_")) {
                    String[] recipeParts = fileName.split("-recipe_");
                    if (recipeParts.length > 1) {
                        String recipeNum = recipeParts[1].replace(".pdf", "");
                        System.out.println("✅ ID extrait depuis nom de fichier recette: " + recipeNum);
                        return recipeNum;
                    }
                }
                System.out.println("✅ ID extrait depuis nom de fichier complet: " + fileName);
                return fileName;
            }
        }
        String[] segments = fileUrl.split("/");
        if (segments.length > 0) {
            String lastSegment = segments[segments.length - 1];
            System.out.println("✅ ID extrait depuis dernier segment: " + lastSegment);
            return lastSegment;
        }
        System.err.println("❌ Impossible d'extraire un ID depuis l'URL: " + fileUrl);
        return null;
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
    private String constructPublicUrl(Map<String, Object> responseBody) {
        System.out.println("🔗 Construction URL publique depuis réponse: " + responseBody);
        if (responseBody.containsKey("url")) {
            String directUrl = (String) responseBody.get("url");
            System.out.println("✅ URL directe trouvée dans 'url': " + directUrl);
            return directUrl;
        }
        if (responseBody.containsKey("fileUrl")) {
            String fileUrl = (String) responseBody.get("fileUrl");
            System.out.println("✅ URL directe trouvée dans 'fileUrl': " + fileUrl);
            return fileUrl;
        }
        if (responseBody.containsKey("idExterne")) {
            String idExterne = String.valueOf(responseBody.get("idExterne"));
            String url = bucketBaseUrl + "/public/file/" + idExterne;
            System.out.println("✅ URL construite depuis idExterne: " + url);
            return url;
        }
        if (responseBody.containsKey("fileName")) {
            String fileName = (String) responseBody.get("fileName");
            String url = bucketBaseUrl + "/public/file/" + fileName;
            System.out.println("✅ URL construite depuis fileName: " + url);
            return url;
        }
        if (responseBody.containsKey("id")) {
            String id = String.valueOf(responseBody.get("id"));
            String url = bucketBaseUrl + "/public/file/" + id;
            System.out.println("✅ URL construite depuis ID: " + url);
            return url;
        }
        System.err.println("❌ Impossible de construire l'URL publique depuis la réponse");
        System.err.println("🔍 Clés disponibles: " + responseBody.keySet());
        return bucketBaseUrl + "/public/file/unknown";
    }
}