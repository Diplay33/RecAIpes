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

            // Notre identifiant externe personnalisé
            String customExternalId = generateExternalIdNumeric();
            body.add("idExterne", customExternalId);

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

                // CORRECTION: Récupérer l'ID externe retourné par le serveur
                String serverId = null;
                if (responseBody.containsKey("idExterne")) {
                    serverId = String.valueOf(responseBody.get("idExterne"));
                    System.out.println("🔑 ID externe du serveur: " + serverId);
                }

                // Construire l'URL publique
                String publicUrl = constructPublicUrl(responseBody);

                // On retourne les deux valeurs, séparées par un délimiteur spécial
                return publicUrl + "||" + serverId;
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
            System.out.println("🚀 Tentative de suppression avancée pour: " + fileUrl);

            // Extraire l'ID de l'URL (au cas où il contiendrait un ID)
            String idFromUrl = null;
            if (fileUrl.contains("||")) {
                String[] parts = fileUrl.split("\\|\\|");
                idFromUrl = parts.length > 1 ? parts[1] : null;
                fileUrl = parts[0]; // Garder seulement l'URL
            }

            // Essayer les différentes méthodes pour obtenir l'ID
            List<String> idsToTry = new ArrayList<>();

            // 1. D'abord essayer l'ID provenant de l'URL (priorité la plus haute)
            if (idFromUrl != null) {
                idsToTry.add(idFromUrl);
                System.out.println("🔑 Utilisation de l'ID stocké: " + idFromUrl);
            }

            // 2. Essayer de récupérer l'ID en cherchant dans les fichiers
            try {
                Map<String, Object> searchResults = searchFilesPrivate(null, null, null);
                if (searchResults != null && searchResults.containsKey("files")) {
                    List<Map<String, Object>> files = (List<Map<String, Object>>) searchResults.get("files");

                    // Extraire le nom du fichier de l'URL
                    String fileName = fileUrl.substring(fileUrl.lastIndexOf('/') + 1);
                    System.out.println("📄 Recherche de fichier par nom: " + fileName);

                    for (Map<String, Object> file : files) {
                        if (file.containsKey("fileName") && file.get("fileName").equals(fileName)) {
                            String fileId = String.valueOf(file.get("idExterne"));
                            if (fileId != null && !idsToTry.contains(fileId)) {
                                idsToTry.add(fileId);
                                System.out.println("🔍 ID trouvé dans les fichiers: " + fileId);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                System.out.println("⚠️ Erreur lors de la recherche des fichiers: " + e.getMessage());
            }

            // 3. Extraire l'ID du nom de fichier (dernier recours)
            String extractedId = extractFileIdFromUrl(fileUrl);
            if (extractedId != null && !idsToTry.contains(extractedId)) {
                idsToTry.add(extractedId);
                System.out.println("📄 ID extrait du nom de fichier: " + extractedId);
            }

            // 4. Essayer les IDs numériques pour le fallback
            for (int i = 0; i < 5; i++) {
                String numericId = String.valueOf(i + 1);
                if (!idsToTry.contains(numericId)) {
                    idsToTry.add(numericId);
                }
            }

            // Essayer tous les IDs possibles
            System.out.println("🔄 Tentative avec " + idsToTry.size() + " IDs possibles: " + idsToTry);
            boolean anySuccess = false;

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
                        anySuccess = true;
                        break;  // Sortir de la boucle si une suppression réussit
                    }
                } catch (Exception e) {
                    System.out.println("⚠️ Échec avec ID " + id + ": " + e.getMessage());
                }
            }

            // Vérifier si le fichier est toujours accessible
            if (anySuccess) {
                try {
                    Thread.sleep(2000); // Attendre que la suppression soit propagée
                    HttpHeaders verifyHeaders = new HttpHeaders();
                    HttpEntity<Void> verifyRequest = new HttpEntity<>(verifyHeaders);

                    System.out.println("🔍 Vérification de l'accessibilité: " + fileUrl);

                    ResponseEntity<byte[]> verifyResponse = restTemplate.exchange(
                            fileUrl, HttpMethod.HEAD, verifyRequest, byte[].class);

                    if (verifyResponse.getStatusCode().is2xxSuccessful()) {
                        System.err.println("⚠️ ATTENTION: Le fichier est toujours accessible malgré la suppression réussie!");
                        System.err.println("⚠️ Cela peut être dû à une mise en cache ou à un délai de propagation.");
                    }
                } catch (Exception e) {
                    System.out.println("✅ Vérification: Le fichier n'est plus accessible");
                }
            }

            return anySuccess;
        } catch (Exception e) {
            System.err.println("❌ Erreur lors de la suppression: " + e.getMessage());
            return false;
        }
    }

    /**
     * Extraire l'ID du fichier depuis l'URL pour la suppression
     */
    private String extractFileIdFromUrl(String fileUrl) {
        if (fileUrl == null || fileUrl.isEmpty()) {
            return null;
        }

        System.out.println("🔍 Extraction ID depuis URL: " + fileUrl);

        // Cas 1: URL avec /public/file/{id}
        if (fileUrl.contains("/public/file/")) {
            String[] parts = fileUrl.split("/public/file/");
            if (parts.length > 1) {
                String id = parts[1];
                System.out.println("✅ ID extrait depuis /public/file/: " + id);
                return id;
            }
        }

        // Cas 2: URL directe du student-bucket (ex: student-bucket/pdfs/uuid-filename.pdf)
        if (fileUrl.contains("/student-bucket/")) {
            // Pour les URLs directes, on va essayer d'utiliser le nom du fichier
            String[] parts = fileUrl.split("/");
            if (parts.length > 0) {
                String fileName = parts[parts.length - 1]; // Dernier segment
                // Extraire juste le nom sans l'extension ni l'UUID
                if (fileName.contains("-recipe_")) {
                    // Format: uuid-recipe_X.pdf -> extraire le numéro X
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

        // Cas 3: Si l'URL ne correspond à aucun format, extraire le dernier segment
        String[] segments = fileUrl.split("/");
        if (segments.length > 0) {
            String lastSegment = segments[segments.length - 1];
            System.out.println("✅ ID extrait depuis dernier segment: " + lastSegment);
            return lastSegment;
        }

        System.err.println("❌ Impossible d'extraire un ID depuis l'URL: " + fileUrl);
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
        System.out.println("🔗 Construction URL publique depuis réponse: " + responseBody);

        // Priorité 1: URL directe fournie par l'API (champ "url")
        if (responseBody.containsKey("url")) {
            String directUrl = (String) responseBody.get("url");
            System.out.println("✅ URL directe trouvée dans 'url': " + directUrl);
            return directUrl;
        }

        // Priorité 2: URL directe fournie par l'API (champ "fileUrl")
        if (responseBody.containsKey("fileUrl")) {
            String fileUrl = (String) responseBody.get("fileUrl");
            System.out.println("✅ URL directe trouvée dans 'fileUrl': " + fileUrl);
            return fileUrl;
        }

        // Priorité 3: ID externe fourni
        if (responseBody.containsKey("idExterne")) {
            String idExterne = String.valueOf(responseBody.get("idExterne"));
            String url = bucketBaseUrl + "/public/file/" + idExterne;
            System.out.println("✅ URL construite depuis idExterne: " + url);
            return url;
        }

        // Priorité 4: Nom de fichier fourni
        if (responseBody.containsKey("fileName")) {
            String fileName = (String) responseBody.get("fileName");
            String url = bucketBaseUrl + "/public/file/" + fileName;
            System.out.println("✅ URL construite depuis fileName: " + url);
            return url;
        }

        // Priorité 5: ID fourni
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