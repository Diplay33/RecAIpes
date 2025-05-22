package com.ynov.recaipes.config;

import io.github.cdimascio.dotenv.Dotenv;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EnvConfig {

    private final Dotenv dotenv;

    public EnvConfig() {
        // Initialiser dotenv dans le constructeur
        this.dotenv = Dotenv.configure().ignoreIfMissing().load();

        // Définir TOUTES les propriétés système nécessaires
        if (dotenv.get("OPENAI_API_KEY") != null) {
            System.setProperty("OPENAI_API_KEY", dotenv.get("OPENAI_API_KEY"));
            System.out.println("✅ Clé OpenAI chargée depuis .env");
        } else {
            System.out.println("⚠️  Clé OpenAI non trouvée dans .env");
        }

        if (dotenv.get("AWS_ACCESS_KEY_ID") != null) {
            System.setProperty("AWS_ACCESS_KEY_ID", dotenv.get("AWS_ACCESS_KEY_ID"));
        }

        if (dotenv.get("AWS_SECRET_ACCESS_KEY") != null) {
            System.setProperty("AWS_SECRET_ACCESS_KEY", dotenv.get("AWS_SECRET_ACCESS_KEY"));
        }

        if (dotenv.get("STUDENT_TOKEN") != null) {
            System.setProperty("STUDENT_TOKEN", dotenv.get("STUDENT_TOKEN"));
            System.out.println("✅ Token étudiant chargé depuis .env");
        } else {
            System.out.println("⚠️  Token étudiant non trouvé dans .env");
        }
    }

    @Bean
    public String openaiApiKey() {
        return dotenv.get("OPENAI_API_KEY", "");
    }

    @Bean
    public String studentToken() {
        return dotenv.get("STUDENT_TOKEN", "");
    }
}