package com.ynov.recaipes.service;

import com.fasterxml.jackson.databind.ObjectMapper;
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

    @Value("${openai.api.key:}")
    private String apiKey;

    @Value("${openai.api.url.completions:https://api.openai.com/v1/chat/completions}")
    private String completionsUrl;

    @Value("${openai.api.url.images:https://api.openai.com/v1/images/generations}")
    private String imagesUrl;

    public String generateRecipeText(String ingredients, String diet, String cuisine) {
        HttpHeaders headers = createHeaders();

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", "gpt-4");
        requestBody.put("messages", List.of(
                Map.of(
                        "role", "system",
                        "content", "You are a professional chef. Create a complete recipe with title, ingredients, and step-by-step instructions."
                ),
                Map.of(
                        "role", "user",
                        "content", String.format("Create a detailed recipe using these ingredients: %s. Diet: %s. Cuisine style: %s. Format your response with: TITLE, INGREDIENTS (with quantities), INSTRUCTIONS (numbered steps), and a brief DESCRIPTION at the end.",
                                ingredients, diet, cuisine)
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
        requestBody.put("model", "dall-e-3");
        requestBody.put("prompt", "High quality, appetizing food photography of " + recipeTitle + ", professional lighting, top view, on a beautiful plate with garnish");
        requestBody.put("n", 1);
        requestBody.put("size", "1024x1024");

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