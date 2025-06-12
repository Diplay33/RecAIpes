package com.ynov.recaipes;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class RecaipesApplication {

	public static void main(String[] args) {
		// Charger les variables d'environnement avant le démarrage de Spring
		Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
		if (dotenv.get("OPENAI_API_KEY") != null) {
			System.setProperty("OPENAI_API_KEY", dotenv.get("OPENAI_API_KEY"));
		}

		SpringApplication.run(RecaipesApplication.class, args);
	}
}