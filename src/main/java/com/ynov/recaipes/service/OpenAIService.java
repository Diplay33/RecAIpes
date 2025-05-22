package com.ynov.recaipes.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.cdimascio.dotenv.Dotenv;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class OpenAIService {

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    // Supprimez ou commentez cette ligne
    // @Value("${openai.api.key:}")
    private String apiKey;

    @Value("${openai.api.url.completions:https://api.openai.com/v1/chat/completions}")
    private String completionsUrl;

    @Value("${openai.api.url.images:https://api.openai.com/v1/images/generations}")
    private String imagesUrl;

    // Ajoutez cette méthode
    @PostConstruct
    public void init() {
        try {
            Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
            this.apiKey = dotenv.get("OPENAI_API_KEY");
            if (this.apiKey == null || this.apiKey.isEmpty()) {
                System.err.println("ATTENTION: La clé API OpenAI n'a pas été trouvée dans le fichier .env");
            } else {
                System.out.println("Clé API OpenAI chargée avec succès depuis le fichier .env");
            }
        } catch (Exception e) {
            System.err.println("Erreur lors du chargement de la clé API: " + e.getMessage());
        }
    }

    public String generateRecipeText(String dishName) {
        HttpHeaders headers = createHeaders();

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", "gpt-4");

        String prompt = String.format(
                "Crée une recette détaillée en français pour '%s'" +
                        "Format ta réponse avec: TITRE (le nom du plat), INGREDIENTS (avec quantités), INSTRUCTIONS (étapes numérotées), " +
                        "et une brève DESCRIPTION à la fin.",
                dishName
        );

        requestBody.put("messages", List.of(
                Map.of(
                        "role", "system",
                        "content", "Tu es un chef professionnel spécialisé dans la cuisine du monde entier. " +
                                "Crée des recettes détaillées, authentiques et savoureuses. En respectant a la lettre les ingredients de base des plats, si tu as un doute verifier sur marmiton et autres sites"
                ),
                Map.of(
                        "role", "user",
                        "content", prompt
                )
        ));

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

        try {
            Map<String, Object> response = restTemplate.postForObject(completionsUrl, request, Map.class);
            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
            Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
            return (String) message.get("content");
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate recipe text: " + e.getMessage(), e);
        }
    }

    public String generateRecipeImage(String recipeTitle) {
        HttpHeaders headers = createHeaders();

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", "dall-e-2");
        requestBody.put("prompt", "Appetizing picture of" + recipeTitle + "with a professional light, presenting all the dish and sides, only show the ingredients used in the dish");
        requestBody.put("n", 1);
        requestBody.put("size", "256x256");

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

        try {
            Map<String, Object> response = restTemplate.postForObject(imagesUrl, request, Map.class);
            List<Map<String, Object>> data = (List<Map<String, Object>>) response.get("data");
            return (String) data.get(0).get("url");
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate recipe image: " + e.getMessage(), e);
        }
    }

    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + apiKey);
        return headers;
    }
}