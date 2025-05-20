package com.ynov.recaipes.config;

import io.github.cdimascio.dotenv.Dotenv;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EnvConfig {

    private final Dotenv dotenv;

    public EnvConfig() {
        // Initialiser dotenv dans le constructeur plutôt que comme un bean
        this.dotenv = Dotenv.configure().ignoreIfMissing().load();

        // Définir les propriétés système nécessaires
        if (dotenv.get("OPENAI_API_KEY") != null) {
            System.setProperty("OPENAI_API_KEY", dotenv.get("OPENAI_API_KEY"));
        }
    }

    @Bean
    public String openaiApiKey() {
        return dotenv.get("OPENAI_API_KEY", "");
    }
}